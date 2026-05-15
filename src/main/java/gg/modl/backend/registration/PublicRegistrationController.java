package gg.modl.backend.registration;

import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.infrastructure.turnstile.TurnstileService;
import gg.modl.backend.infrastructure.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_REGISTRATION)
@RequiredArgsConstructor
@Slf4j
public class PublicRegistrationController {
    private final ServerService serverService;
    private final TurnstileService turnstileService;
    private final SessionService sessionService;
    private final RegistrationService registrationService;
    private final CookieUtil cookieUtil;
    private static final Set<String> RESERVED_SUBDOMAINS = Set.of(
        "payments", "payment", "api", "app",
        "status", "mail", "www", "discord",
        "admin", "twitter", "demo", "panel",
        "ftp", "sftp", "www2", "www3",
        "billing", "stripe", "test", "staging",
        "root", "internal", "administrator", "mod",
        "beta", "dev", "portal", "dashboard",
        "modl", "support", "help", "email",
        "docs", "secure", "alpha", "cdn",
        "nexus", "replay", "replays"
    );

    @PostMapping
    public ResponseEntity<?> register(
        HttpServletRequest request,
        @RequestBody @Valid RegisterRequest requestData) {

        String clientIp = RequestUtil.getClientIp(request);

        RegistrationService.RateLimitResult rateLimit = registrationService.checkRateLimit(clientIp);
        if (rateLimit.limited()) {
            return ResponseEntity.status(429).body(new RegisterResponse(
                false,
                "Rate limit exceeded. You can only register one server every 10 minutes. Please try again in " + rateLimit.remainingMinutes() + " minute(s).",
                null
            ));
        }

        if (!turnstileService.validateToken(requestData.turnstileToken(), clientIp)) {
            log.warn("Turnstile validation failed for registration attempt");
            return ResponseEntity.badRequest().body(new RegisterResponse(
                false,
                "Security verification failed. Please try again.",
                null
            ));
        }

        ResponseEntity<?> reservedSubdomainResponse = checkReservedSubdomain(requestData.customDomain());
        if (reservedSubdomainResponse != null) {
            return reservedSubdomainResponse;
        }

        ServerService.ServerExistResult existResult = serverService.doesServerExist(
            requestData.email(),
            requestData.serverName(),
            requestData.customDomain()
        );

        ResponseEntity<?> duplicateResponse = checkDuplicates(existResult);
        if (duplicateResponse != null) {
            return duplicateResponse;
        }

        String emailVerificationToken = registrationService.generateToken();
        ServerPlan plan = registrationService.parsePlan(requestData.plan());

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

        registrationService.recordRateLimit(clientIp);
        registrationService.sendVerificationEmail(requestData.email(), requestData.customDomain(), emailVerificationToken);

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

        String autoLoginToken = registrationService.generateToken();
        Date tokenExpiry = registrationService.createAutoLoginTokenExpiry();
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

        if (registrationService.isTokenExpired(server)) {
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
            registrationService.getProvisioningMessage(server.getProvisioningStatus())
        ));
    }

    @PostMapping("/auto-login")
    public ResponseEntity<?> autoLogin(
        @RequestBody @Valid AutoLoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse response) {

        Server server = serverService.getServerByAutoLoginToken(request.token());
        if (server == null) {
            return ResponseEntity.badRequest().body(new AutoLoginResponse(false, "Invalid or expired token.", null));
        }

        if (registrationService.isTokenExpired(server)) {
            return ResponseEntity.badRequest().body(new AutoLoginResponse(
                false, "Setup token has expired. Please sign in manually.", null
            ));
        }

        RegistrationService.ServerReadiness readiness = registrationService.checkServerReadiness(server);
        if (readiness != RegistrationService.ServerReadiness.READY) {
            String message = switch (readiness) {
                case PROVISIONING_INCOMPLETE -> "Server setup is not yet complete.";
                case EMAIL_UNVERIFIED -> "Email verification is required.";
                default -> "Server is not ready.";
            };
            return ResponseEntity.badRequest().body(new AutoLoginResponse(false, message, null));
        }

        AuthSessionData session = sessionService.createSession(server, server.getAdminEmail(), RequestUtil.getClientIp(httpRequest),
            httpRequest.getHeader("User-Agent"));

        serverService.clearAutoLoginToken(server);

        response.addCookie(cookieUtil.createSessionCookie(session.getId()));

        return ResponseEntity.ok(new AutoLoginResponse(true, "Login successful.", "/panel"));
    }

    @PostMapping("/cli")
    public ResponseEntity<?> registerCli(
        HttpServletRequest request,
        @RequestBody @Valid CliRegisterRequest requestData) {

        String clientIp = RequestUtil.getClientIp(request);

        RegistrationService.RateLimitResult rateLimit = registrationService.checkCliRateLimit(clientIp);
        if (rateLimit.limited()) {
            return ResponseEntity.status(429).body(new RegisterResponse(
                false,
                "Rate limit exceeded. CLI registration is limited to once every 30 minutes. Please try again in " + rateLimit.remainingMinutes() + " minute(s).",
                null
            ));
        }

        ResponseEntity<?> reservedSubdomainResponse = checkReservedSubdomain(requestData.customDomain());
        if (reservedSubdomainResponse != null) {
            return reservedSubdomainResponse;
        }

        ServerService.ServerExistResult existResult = serverService.doesServerExist(
            requestData.email(),
            requestData.serverName(),
            requestData.customDomain()
        );

        ResponseEntity<?> duplicateResponse = checkDuplicates(existResult);
        if (duplicateResponse != null) {
            return duplicateResponse;
        }

        String emailVerificationToken = registrationService.generateToken();
        ServerPlan plan = registrationService.parsePlan(requestData.plan());

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
            log.error("Failed to create server via CLI", e);
            return ResponseEntity.internalServerError().body(new RegisterResponse(
                false, "An internal server error occurred during registration. Please try again later.", null
            ));
        }

        registrationService.recordCliRateLimit(clientIp);

        String cliSetupToken = registrationService.generateToken();
        serverService.setCliSetupToken(server, cliSetupToken);

        registrationService.sendVerificationEmail(requestData.email(), requestData.customDomain(), emailVerificationToken);

        return ResponseEntity.status(201).body(new CliRegisterResponse(
            true,
            "Registration successful. Please check your email to verify your account.",
            new ServerInfo(server.getId(), server.getServerName()),
            cliSetupToken
        ));
    }

    @PostMapping("/cli/status")
    public ResponseEntity<?> cliSetupStatus(@RequestBody @Valid TokenRequest request) {
        Server server = serverService.getServerByCliSetupToken(request.token());
        if (server == null) {
            return ResponseEntity.badRequest().body(new CliStatusResponse(
                false, null, null, null, "Invalid or expired setup token."
            ));
        }

        boolean emailVerified = Boolean.TRUE.equals(server.getEmailVerified());
        String status = server.getProvisioningStatus() != null
            ? server.getProvisioningStatus().name() : ProvisioningStatus.PENDING.name();
        boolean complete = emailVerified && server.getProvisioningStatus() == ProvisioningStatus.COMPLETED;

        String apiKey = null;
        String panelUrl = null;
        if (complete) {
            apiKey = registrationService.resolveOrGenerateApiKey(server);
            panelUrl = registrationService.buildPanelUrl(server);
            serverService.clearCliSetupToken(server);
        }

        return ResponseEntity.ok(new CliStatusResponse(
            true, emailVerified, status, complete ? apiKey : null,
            complete ? panelUrl : registrationService.getProvisioningMessage(server.getProvisioningStatus())
        ));
    }

    @PostMapping("/api-key")
    public ResponseEntity<?> getApiKeyFromToken(@RequestBody @Valid TokenRequest request) {
        Server server = serverService.getServerByAutoLoginToken(request.token());
        if (server == null) {
            return ResponseEntity.badRequest().body(new ApiKeyResponse(false, null, null, "Invalid or expired token."));
        }

        if (registrationService.isTokenExpired(server)) {
            return ResponseEntity.badRequest().body(new ApiKeyResponse(false, null, null, "Token has expired."));
        }

        RegistrationService.ServerReadiness readiness = registrationService.checkServerReadiness(server);
        if (readiness != RegistrationService.ServerReadiness.READY) {
            String message = switch (readiness) {
                case PROVISIONING_INCOMPLETE -> "Server setup is not yet complete.";
                case EMAIL_UNVERIFIED -> "Email verification is required.";
                default -> "Server is not ready.";
            };
            return ResponseEntity.badRequest().body(new ApiKeyResponse(false, null, null, message));
        }

        String apiKey = registrationService.resolveOrGenerateApiKey(server);
        String panelUrl = registrationService.buildPanelUrl(server);

        serverService.clearAutoLoginToken(server);

        return ResponseEntity.ok(new ApiKeyResponse(true, apiKey, panelUrl, "API key retrieved successfully."));
    }

    private ResponseEntity<?> checkDuplicates(ServerService.ServerExistResult existResult) {
        if (existResult.emailMatch()) {
            return ResponseEntity.status(409).body(new RegisterResponse(false, "An account with this email already exists.", null));
        }
        if (existResult.nameMatch()) {
            return ResponseEntity.status(409).body(new RegisterResponse(false, "This server name is already taken.", null));
        }
        if (existResult.domainMatch()) {
            return ResponseEntity.status(409).body(new RegisterResponse(false, "This subdomain is already in use.", null));
        }
        return null;
    }

    private ResponseEntity<?> checkReservedSubdomain(String customDomain) {
        if (customDomain != null && RESERVED_SUBDOMAINS.contains(customDomain)) {
            return ResponseEntity.status(409).body(new RegisterResponse(
                false,
                "This subdomain is reserved and cannot be used.",
                null
            ));
        }
        return null;
    }

    public record CliRegisterRequest(
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(min = 3, max = 100) String serverName,
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-z0-9-]+$", message = "Subdomain can only contain lowercase letters, numbers, and hyphens") String customDomain,
        @Size(max = 32) String plan
    ) {}

    public record ApiKeyResponse(boolean success, String apiKey, String panelUrl, String message) {}

    public record RegisterRequest(
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(min = 3, max = 100) String serverName,
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-z0-9-]+$", message = "Subdomain can only contain lowercase letters, numbers, and hyphens") String customDomain,
        @Size(max = 32) String plan,
        @NotBlank @Size(max = 4096) String turnstileToken
    ) {}

    public record RegisterResponse(boolean success, String message, ServerInfo server) {}

    public record ServerInfo(String id, String name) {}

    public record VerifyResponse(boolean success, String message, String subdomain, String autoLoginToken) {}

    public record SetupStatusResponse(String subdomain, String serverName, Boolean emailVerified, String provisioningStatus, String message) {}

    public record TokenRequest(@NotBlank @Size(max = 512) String token) {}

    public record AutoLoginRequest(@NotBlank @Size(max = 512) String token) {}

    public record AutoLoginResponse(boolean success, String message, String redirectUrl) {}

    public record CliRegisterResponse(boolean success, String message, ServerInfo server, String setupToken) {}

    public record CliStatusResponse(boolean success, Boolean emailVerified, String provisioningStatus, String apiKey, String message) {}
}
