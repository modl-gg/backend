package gg.modl.backend.registration;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.turnstile.TurnstileService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_REGISTRATION)
@RequiredArgsConstructor
@Slf4j
public class PublicRegistrationController {
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long RATE_LIMIT_WINDOW_MS = 10 * 60 * 1000; // 10 minutes
    private static final long AUTO_LOGIN_TOKEN_EXPIRY_MS = 10 * 60 * 1000; // 10 minutes

    private final ServerService serverService;
    private final TurnstileService turnstileService;
    private final EmailService emailService;
    private final SessionService sessionService;
    private final AuthConfiguration authConfiguration;
    private static final int MAX_RATE_LIMIT_ENTRIES = 10_000;
    private final Map<String, Long> rateLimitMap = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_RATE_LIMIT_ENTRIES;
                }
            }
    );

    @Value("${modl.app-domain}")
    private String appDomain;

    @PostMapping
    public ResponseEntity<?> register(
            HttpServletRequest request,
            @RequestBody @Valid RegisterRequest requestData) {

        String clientIp = RequestUtil.getClientIp(request);

        // Check rate limit
        long now = System.currentTimeMillis();
        Long lastRegistration = rateLimitMap.get(clientIp);
        if (lastRegistration != null && (now - lastRegistration) < RATE_LIMIT_WINDOW_MS) {
            long remainingMinutes = (RATE_LIMIT_WINDOW_MS - (now - lastRegistration)) / 1000 / 60 + 1;
            return ResponseEntity.status(429).body(new RegisterResponse(
                    false,
                    "Rate limit exceeded. You can only register one server every 10 minutes. Please try again in " + remainingMinutes + " minute(s).",
                    null
            ));
        }

        // Validate Turnstile token
        if (!turnstileService.validateToken(requestData.turnstileToken(), clientIp)) {
            log.warn("Turnstile validation failed for IP: {}", clientIp);
            return ResponseEntity.badRequest().body(new RegisterResponse(
                    false,
                    "Security verification failed. Please try again.",
                    null
            ));
        }

        // Check for duplicates
        ServerService.ServerExistResult existResult = serverService.doesServerExist(
                requestData.email(),
                requestData.serverName(),
                requestData.customDomain()
        );

        if (existResult.emailMatch()) {
            return ResponseEntity.status(409).body(new RegisterResponse(
                    false,
                    "An account with this email already exists.",
                    null
            ));
        }
        if (existResult.nameMatch()) {
            return ResponseEntity.status(409).body(new RegisterResponse(
                    false,
                    "This server name is already taken.",
                    null
            ));
        }
        if (existResult.domainMatch()) {
            return ResponseEntity.status(409).body(new RegisterResponse(
                    false,
                    "This subdomain is already in use.",
                    null
            ));
        }

        // Generate email verification token
        String emailVerificationToken = RequestUtil.generateSecureToken(TOKEN_BYTE_LENGTH);

        // Parse plan
        ServerPlan plan = requestData.plan().equalsIgnoreCase("premium") ? ServerPlan.PREMIUM : ServerPlan.FREE;

        // Create server
        Server server;
        try {
            server = serverService.createServer(
                    requestData.serverName(),
                    requestData.customDomain(),
                    requestData.email(),
                    emailVerificationToken,
                    plan
            );
        } catch (Exception e) {
            log.error("Failed to create server", e);
            return ResponseEntity.internalServerError().body(new RegisterResponse(
                    false,
                    "An internal server error occurred during registration. Please try again later.",
                    null
            ));
        }

        // Update rate limit
        rateLimitMap.put(clientIp, now);
        cleanupRateLimitMap(now);

        // Send verification email
        try {
            String verificationLink = String.format("https://%s.%s/verify-email?token=%s",
                    requestData.customDomain(), appDomain, emailVerificationToken);
            sendVerificationEmail(requestData.email(), verificationLink);
        } catch (Exception e) {
            log.error("Failed to send verification email", e);
            // Don't fail the registration, just log the error
        }

        return ResponseEntity.status(201).body(new RegisterResponse(
                true,
                "Registration successful. Please check your email to verify your account.",
                new ServerInfo(server.getId(), server.getServerName())
        ));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        return verifyEmailInternal(token);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyEmailPost(@RequestBody @Valid TokenRequest body) {
        return verifyEmailInternal(body.token());
    }

    private ResponseEntity<?> verifyEmailInternal(String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(new VerifyResponse(false, "Verification token is required.", null, null));
        }

        Server server = serverService.verifyEmailToken(token);
        if (server == null) {
            return ResponseEntity.badRequest().body(new VerifyResponse(false, "Invalid or expired verification token.", null, null));
        }

        // Generate auto-login token for seamless setup flow
        String autoLoginToken = RequestUtil.generateSecureToken(TOKEN_BYTE_LENGTH);
        Date tokenExpiry = new Date(System.currentTimeMillis() + AUTO_LOGIN_TOKEN_EXPIRY_MS);
        serverService.setAutoLoginToken(server, autoLoginToken, tokenExpiry);

        return ResponseEntity.ok(new VerifyResponse(
                true,
                "Email verified successfully.",
                server.getCustomDomain(),
                autoLoginToken
        ));
    }

    @GetMapping("/setup-status")
    public ResponseEntity<?> getSetupStatus(@RequestParam String token) {
        return getSetupStatusInternal(token);
    }

    @PostMapping("/setup-status")
    public ResponseEntity<?> getSetupStatusPost(@RequestBody @Valid TokenRequest body) {
        return getSetupStatusInternal(body.token());
    }

    private ResponseEntity<?> getSetupStatusInternal(String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(new SetupStatusResponse(
                    null, null, false, null, "Token is required."
            ));
        }

        Server server = serverService.getServerByAutoLoginToken(token);
        if (server == null) {
            return ResponseEntity.badRequest().body(new SetupStatusResponse(
                    null, null, false, null, "Invalid or expired setup token."
            ));
        }

        // Check if token has expired
        if (server.getProvisioningSignInTokenExpiresAt() != null &&
                server.getProvisioningSignInTokenExpiresAt().before(new Date())) {
            return ResponseEntity.badRequest().body(new SetupStatusResponse(
                    server.getCustomDomain(), server.getServerName(),
                    server.getEmailVerified(), null, "Setup token has expired. Please request a new verification email."
            ));
        }

        return ResponseEntity.ok(new SetupStatusResponse(
                server.getCustomDomain(),
                server.getServerName(),
                server.getEmailVerified(),
                server.getProvisioningStatus() != null ? server.getProvisioningStatus().name() : ProvisioningStatus.PENDING.name(),
                getProvisioningMessage(server.getProvisioningStatus())
        ));
    }

    @PostMapping("/auto-login")
    public ResponseEntity<?> autoLogin(
            @RequestBody @Valid AutoLoginRequest request,
            HttpServletResponse response) {

        Server server = serverService.getServerByAutoLoginToken(request.token());
        if (server == null) {
            return ResponseEntity.badRequest().body(new AutoLoginResponse(false, "Invalid or expired token.", null));
        }

        // Check token expiry
        if (server.getProvisioningSignInTokenExpiresAt() != null &&
                server.getProvisioningSignInTokenExpiresAt().before(new Date())) {
            return ResponseEntity.badRequest().body(new AutoLoginResponse(
                    false, "Setup token has expired. Please sign in manually.", null
            ));
        }

        // Verify provisioning is complete and email is verified
        if (server.getProvisioningStatus() != ProvisioningStatus.COMPLETED) {
            return ResponseEntity.badRequest().body(new AutoLoginResponse(
                    false, "Server setup is not yet complete.", null
            ));
        }

        if (!Boolean.TRUE.equals(server.getEmailVerified())) {
            return ResponseEntity.badRequest().body(new AutoLoginResponse(
                    false, "Email verification is required.", null
            ));
        }

        // Create session for the admin user
        AuthSessionData session = sessionService.createSession(server, server.getAdminEmail());

        // Clear the auto-login token (one-time use)
        serverService.clearAutoLoginToken(server);

        // Set session cookie
        Cookie sessionCookie = createSessionCookie(session.getId());
        response.addCookie(sessionCookie);

        return ResponseEntity.ok(new AutoLoginResponse(true, "Login successful.", "/panel"));
    }

    private String getProvisioningMessage(ProvisioningStatus status) {
        if (status == null) return "Your server is queued for setup...";
        return switch (status) {
            case PENDING -> "Your server is queued for setup...";
            case IN_PROGRESS -> "Setting up your server...";
            case COMPLETED -> "Setup complete!";
            case FAILED -> "Setup failed. Please contact support.";
        };
    }

    private Cookie createSessionCookie(String sessionId) {
        Cookie cookie = new Cookie(authConfiguration.getSessionCookieName(), sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(authConfiguration.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge((int) authConfiguration.getSessionDurationSeconds());
        cookie.setAttribute("SameSite", authConfiguration.isDevelopmentMode() ? "Lax" : "Strict");
        String cookieDomain = authConfiguration.getCookieDomain();
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.setDomain(cookieDomain);
        }
        return cookie;
    }

    private void sendVerificationEmail(String email, String verificationLink) throws Exception {
        EmailHTMLTemplate.HTMLEmail htmlEmail = EmailHTMLTemplate.REGISTRATION_VERIFY_LINK.build(verificationLink);
        emailService.send(email, htmlEmail);
    }

    private void cleanupRateLimitMap(long now) {
        rateLimitMap.entrySet().removeIf(entry -> now - entry.getValue() > RATE_LIMIT_WINDOW_MS);
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 3, max = 100) String serverName,
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-z0-9-]+$", message = "Subdomain can only contain lowercase letters, numbers, and hyphens") String customDomain,
            String plan,
            @NotBlank String turnstileToken
    ) {}

    public record RegisterResponse(boolean success, String message, ServerInfo server) {}

    public record ServerInfo(String id, String name) {}

    public record VerifyResponse(boolean success, String message, String subdomain, String autoLoginToken) {}

    public record SetupStatusResponse(String subdomain, String serverName, Boolean emailVerified, String provisioningStatus, String message) {}

    public record TokenRequest(@NotBlank String token) {}

    public record AutoLoginRequest(@NotBlank String token) {}

    public record AutoLoginResponse(boolean success, String message, String redirectUrl) {}
}
