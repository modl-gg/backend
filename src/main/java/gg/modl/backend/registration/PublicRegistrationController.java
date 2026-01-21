package gg.modl.backend.registration;

import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.turnstile.TurnstileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_REGISTRATION)
@RequiredArgsConstructor
@Slf4j
public class PublicRegistrationController {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long RATE_LIMIT_WINDOW_MS = 10 * 60 * 1000; // 10 minutes

    private final ServerService serverService;
    private final TurnstileService turnstileService;
    private final EmailService emailService;
    private final Map<String, Long> rateLimitMap = new ConcurrentHashMap<>();

    @Value("${modl.app-domain}")
    private String appDomain;

    @PostMapping
    public ResponseEntity<?> register(
            HttpServletRequest request,
            @RequestBody @Valid RegisterRequest requestData,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new RegisterResponse(false, "Validation failed", null));
        }

        String clientIp = getClientIp(request);
        log.info("Registration attempt from IP: {}", clientIp);

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
        var existResult = serverService.doesServerExist(
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
        String emailVerificationToken = generateSecureToken();

        // Parse plan
        ServerPlan plan = requestData.plan().equalsIgnoreCase("premium") ? ServerPlan.premium : ServerPlan.free;

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
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(new VerifyResponse(false, "Verification token is required."));
        }

        boolean verified = serverService.verifyEmailToken(token);
        if (!verified) {
            return ResponseEntity.badRequest().body(new VerifyResponse(false, "Invalid or expired verification token."));
        }

        return ResponseEntity.ok(new VerifyResponse(true, "Email verified successfully. You can now access your panel."));
    }

    private void sendVerificationEmail(String email, String verificationLink) throws Exception {
        EmailHTMLTemplate.HTMLEmail htmlEmail = EmailHTMLTemplate.REGISTRATION_VERIFY_LINK.build(verificationLink);
        emailService.send(email, htmlEmail);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private String generateSecureToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
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

    public record VerifyResponse(boolean success, String message) {}
}
