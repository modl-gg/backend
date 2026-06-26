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
import gg.modl.proto.modl.v1.AutoLoginRequest;
import gg.modl.proto.modl.v1.CliRegistrationRequest;
import gg.modl.proto.modl.v1.CliSetupStatusRequest;
import gg.modl.proto.modl.v1.EmailVerificationTokenRequest;
import gg.modl.proto.modl.v1.ProvisioningTokenRequest;
import gg.modl.proto.modl.v1.PublicRegistrationRequest;
import gg.modl.proto.modl.v1.ServerAvailabilityRequest;
import gg.modl.proto.modl.v1.ServerAvailabilityResponse;
import gg.modl.proto.modl.v1.SetupStatusRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
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
    private static final int SUBDOMAIN_MIN_LEN = 3;
    private static final int SUBDOMAIN_MAX_LEN = 50;
    private static final Pattern SUBDOMAIN_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

    @PostMapping
    public ResponseEntity<?> register(
        HttpServletRequest request,
        @RequestBody PublicRegistrationRequest requestData) {

        String clientIp = RequestUtil.getClientIp(request);

        RegistrationService.RateLimitResult rateLimit = registrationService.reserveRateLimit(clientIp);
        if (rateLimit.limited()) {
            return ResponseEntity.status(429).body(RegistrationProtoMapper.toRegistrationResponse(
                false,
                "Rate limit exceeded. You can only register one server every 10 minutes. Please try again in " + rateLimit.remainingMinutes() + " minute(s).",
                null
            ));
        }

        if (!turnstileService.validateToken(requestData.getTurnstileToken(), clientIp)) {
            log.warn("Turnstile validation failed for registration attempt");
            registrationService.releaseRateLimit(clientIp);
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toRegistrationResponse(
                false,
                "Security verification failed. Please try again.",
                null
            ));
        }

        ResponseEntity<?> subdomainValidationResponse = validateSubdomain(requestData.getCustomDomain());
        if (subdomainValidationResponse != null) {
            registrationService.releaseRateLimit(clientIp);
            return subdomainValidationResponse;
        }

        ResponseEntity<?> reservedSubdomainResponse = checkReservedSubdomain(requestData.getCustomDomain());
        if (reservedSubdomainResponse != null) {
            registrationService.releaseRateLimit(clientIp);
            return reservedSubdomainResponse;
        }

        ServerService.ServerExistResult existResult = serverService.doesServerExist(
            requestData.getEmail(),
            requestData.getServerName(),
            requestData.getCustomDomain()
        );

        ResponseEntity<?> duplicateResponse = checkDuplicates(existResult);
        if (duplicateResponse != null) {
            registrationService.releaseRateLimit(clientIp);
            return duplicateResponse;
        }

        String emailVerificationToken = registrationService.generateToken();
        ServerPlan plan = registrationService.parsePlan(requestData.hasPlan() ? requestData.getPlan() : null);

        Server server;
        try {
            server = serverService.createServer(
                requestData.getServerName(),
                requestData.getCustomDomain(),
                requestData.getEmail(),
                emailVerificationToken,
                plan
            );
        } catch (Exception e) {
            log.error("Failed to create server", e);
            registrationService.releaseRateLimit(clientIp);
            return ResponseEntity.internalServerError().body(RegistrationProtoMapper.toRegistrationResponse(
                false,
                "An internal server error occurred during registration. Please try again later.",
                null
            ));
        }

        registrationService.sendVerificationEmail(requestData.getEmail(), requestData.getCustomDomain(), emailVerificationToken);

        return ResponseEntity.status(201).body(RegistrationProtoMapper.toRegistrationResponse(
            true,
            "Registration successful. Please check your email to verify your account.",
            server
        ));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        return verifyEmailInternal(token);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyEmailPost(@RequestBody EmailVerificationTokenRequest body) {
        return verifyEmailInternal(body.getToken());
    }

    private ResponseEntity<?> verifyEmailInternal(String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toEmailVerificationResponse(
                false, "Verification token is required.", null, null));
        }

        Server server = serverService.verifyEmailToken(token);
        if (server == null) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toEmailVerificationResponse(
                false, "Invalid or expired verification token.", null, null));
        }

        String autoLoginToken = registrationService.generateToken();
        Date tokenExpiry = registrationService.createAutoLoginTokenExpiry();
        serverService.setAutoLoginToken(server, autoLoginToken, tokenExpiry);

        return ResponseEntity.ok(RegistrationProtoMapper.toEmailVerificationResponse(
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
    public ResponseEntity<?> getSetupStatusPost(@RequestBody SetupStatusRequest body) {
        return getSetupStatusInternal(body.getToken());
    }

    private ResponseEntity<?> getSetupStatusInternal(String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toSetupStatusResponse(
                null, null, false, null, "Token is required."
            ));
        }

        Server server = serverService.getServerByAutoLoginToken(token);
        if (server == null) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toSetupStatusResponse(
                null, null, false, null, "Invalid or expired setup token."
            ));
        }

        if (registrationService.isTokenExpired(server)) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toSetupStatusResponse(
                server.getCustomDomain(), server.getServerName(),
                server.getEmailVerified(), null, "Setup token has expired. Please request a new verification email."
            ));
        }

        return ResponseEntity.ok(RegistrationProtoMapper.toSetupStatusResponse(
            server.getCustomDomain(),
            server.getServerName(),
            server.getEmailVerified(),
            server.getProvisioningStatus() != null ? server.getProvisioningStatus() : ProvisioningStatus.PENDING,
            registrationService.getProvisioningMessage(server.getProvisioningStatus())
        ));
    }

    @PostMapping("/auto-login")
    public ResponseEntity<?> autoLogin(
        @RequestBody AutoLoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse response) {

        Server server = serverService.getServerByAutoLoginToken(request.getToken());
        if (server == null) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toAutoLoginResponse(false, "Invalid or expired token.", null));
        }

        if (registrationService.isTokenExpired(server)) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toAutoLoginResponse(
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
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toAutoLoginResponse(false, message, null));
        }

        server = serverService.consumeAutoLoginToken(request.getToken());
        if (server == null) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toAutoLoginResponse(false, "Invalid or expired token.", null));
        }

        AuthSessionData session = sessionService.createSession(server, server.getAdminEmail(), RequestUtil.getClientIp(httpRequest),
            httpRequest.getHeader("User-Agent"));

        response.addCookie(cookieUtil.createSessionCookie(session.getId()));

        return ResponseEntity.ok(RegistrationProtoMapper.toAutoLoginResponse(true, "Login successful.", "/panel"));
    }

    @PostMapping("/cli")
    public ResponseEntity<?> registerCli(
        HttpServletRequest request,
        @RequestBody CliRegistrationRequest requestData) {

        String clientIp = RequestUtil.getClientIp(request);

        RegistrationService.RateLimitResult rateLimit = registrationService.reserveCliRateLimit(clientIp);
        if (rateLimit.limited()) {
            return ResponseEntity.status(429).body(RegistrationProtoMapper.toRegistrationResponse(
                false,
                "Rate limit exceeded. CLI registration is limited to once every 30 minutes. Please try again in " + rateLimit.remainingMinutes() + " minute(s).",
                null
            ));
        }

        ResponseEntity<?> subdomainValidationResponse = validateSubdomain(requestData.getCustomDomain());
        if (subdomainValidationResponse != null) {
            registrationService.releaseCliRateLimit(clientIp);
            return subdomainValidationResponse;
        }

        ResponseEntity<?> reservedSubdomainResponse = checkReservedSubdomain(requestData.getCustomDomain());
        if (reservedSubdomainResponse != null) {
            registrationService.releaseCliRateLimit(clientIp);
            return reservedSubdomainResponse;
        }

        ServerService.ServerExistResult existResult = serverService.doesServerExist(
            requestData.getEmail(),
            requestData.getServerName(),
            requestData.getCustomDomain()
        );

        ResponseEntity<?> duplicateResponse = checkDuplicates(existResult);
        if (duplicateResponse != null) {
            registrationService.releaseCliRateLimit(clientIp);
            return duplicateResponse;
        }

        String emailVerificationToken = registrationService.generateToken();
        ServerPlan plan = registrationService.parsePlan(requestData.hasPlan() ? requestData.getPlan() : null);

        Server server;
        try {
            server = serverService.createServer(
                requestData.getServerName(),
                requestData.getCustomDomain(),
                requestData.getEmail(),
                emailVerificationToken,
                plan
            );
        } catch (Exception e) {
            log.error("Failed to create server via CLI", e);
            registrationService.releaseCliRateLimit(clientIp);
            return ResponseEntity.internalServerError().body(RegistrationProtoMapper.toRegistrationResponse(
                false, "An internal server error occurred during registration. Please try again later.", null
            ));
        }


        String cliSetupToken = registrationService.generateToken();
        serverService.setCliSetupToken(server, cliSetupToken);

        registrationService.sendVerificationEmail(requestData.getEmail(), requestData.getCustomDomain(), emailVerificationToken);

        return ResponseEntity.status(201).body(RegistrationProtoMapper.toCliRegistrationResponse(
            true,
            "Registration successful. Please check your email to verify your account.",
            server,
            cliSetupToken
        ));
    }

    @PostMapping("/cli/status")
    public ResponseEntity<?> cliSetupStatus(@RequestBody CliSetupStatusRequest request) {
        Server server = serverService.getServerByCliSetupToken(request.getToken());
        if (server == null) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toCliSetupStatusResponse(
                false, null, null, null, "Invalid or expired setup token."
            ));
        }

        boolean emailVerified = Boolean.TRUE.equals(server.getEmailVerified());
        ProvisioningStatus status = server.getProvisioningStatus() != null
            ? server.getProvisioningStatus() : ProvisioningStatus.PENDING;
        boolean complete = emailVerified && server.getProvisioningStatus() == ProvisioningStatus.COMPLETED;

        String apiKey = null;
        String panelUrl = null;
        if (complete) {
            apiKey = registrationService.resolveOrGenerateApiKey(server);
            panelUrl = registrationService.buildPanelUrl(server);
            serverService.clearCliSetupToken(server);
        }

        return ResponseEntity.ok(RegistrationProtoMapper.toCliSetupStatusResponse(
            true, emailVerified, status, complete ? apiKey : null,
            complete ? panelUrl : registrationService.getProvisioningMessage(server.getProvisioningStatus())
        ));
    }

    @PostMapping("/api-key")
    public ResponseEntity<?> getApiKeyFromToken(@RequestBody ProvisioningTokenRequest request) {
        Server server = serverService.getServerByAutoLoginToken(request.getToken());
        if (server == null) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toApiKeyResponse(false, null, null, "Invalid or expired token."));
        }

        if (registrationService.isTokenExpired(server)) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toApiKeyResponse(false, null, null, "Token has expired."));
        }

        RegistrationService.ServerReadiness readiness = registrationService.checkServerReadiness(server);
        if (readiness != RegistrationService.ServerReadiness.READY) {
            String message = switch (readiness) {
                case PROVISIONING_INCOMPLETE -> "Server setup is not yet complete.";
                case EMAIL_UNVERIFIED -> "Email verification is required.";
                default -> "Server is not ready.";
            };
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toApiKeyResponse(false, null, null, message));
        }

        String apiKey = registrationService.resolveOrGenerateApiKey(server);
        String panelUrl = registrationService.buildPanelUrl(server);

        serverService.clearAutoLoginToken(server);

        return ResponseEntity.ok(RegistrationProtoMapper.toApiKeyResponse(true, apiKey, panelUrl, "API key retrieved successfully."));
    }

    private ResponseEntity<?> checkDuplicates(ServerService.ServerExistResult existResult) {
        if (existResult.emailMatch()) {
            return ResponseEntity.status(409).body(RegistrationProtoMapper.toRegistrationResponse(false, "An account with this email already exists.", null));
        }
        if (existResult.nameMatch()) {
            return ResponseEntity.status(409).body(RegistrationProtoMapper.toRegistrationResponse(false, "This server name is already taken.", null));
        }
        if (existResult.domainMatch()) {
            return ResponseEntity.status(409).body(RegistrationProtoMapper.toRegistrationResponse(false, "This subdomain is already in use.", null));
        }
        return null;
    }

    private ResponseEntity<?> validateSubdomain(String customDomain) {
        if (customDomain == null
            || customDomain.length() < SUBDOMAIN_MIN_LEN
            || customDomain.length() > SUBDOMAIN_MAX_LEN
            || !SUBDOMAIN_PATTERN.matcher(customDomain).matches()) {
            return ResponseEntity.badRequest().body(RegistrationProtoMapper.toRegistrationResponse(
                false,
                "Subdomain must be 3-50 characters, lowercase letters, digits and hyphens only, and cannot start or end with a hyphen.",
                null
            ));
        }
        return null;
    }

    private ResponseEntity<?> checkReservedSubdomain(String customDomain) {
        if (customDomain == null) {
            return null;
        }
        String normalized = customDomain.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.startsWith("-") || normalized.endsWith("-")) {
            return ResponseEntity.status(409).body(RegistrationProtoMapper.toRegistrationResponse(
                false,
                "This subdomain is not valid. Use letters, digits, and internal hyphens only.",
                null
            ));
        }
        if (RESERVED_SUBDOMAINS.contains(normalized)) {
            return ResponseEntity.status(409).body(RegistrationProtoMapper.toRegistrationResponse(
                false,
                "This subdomain is reserved and cannot be used.",
                null
            ));
        }
        return null;
    }

    @PostMapping("/check-availability")
    public ResponseEntity<?> checkAvailability(@RequestBody ServerAvailabilityRequest request) {
        String email = request.hasEmail() ? request.getEmail() : null;
        String serverName = request.hasServerName() ? request.getServerName() : null;
        String customDomain = request.hasCustomDomain() ? request.getCustomDomain() : null;

        boolean emailPresent = email != null && !email.isBlank();
        boolean namePresent = serverName != null && !serverName.isBlank();
        boolean domainPresent = customDomain != null && !customDomain.isBlank();

        boolean emailAvailable = true;
        boolean nameAvailable = true;
        boolean subdomainAvailable = true;

        if (emailPresent || namePresent || domainPresent) {
            ServerService.ServerExistResult existResult = serverService.doesServerExist(
                email != null ? email : "",
                serverName != null ? serverName : "",
                customDomain != null ? customDomain : ""
            );
            if (emailPresent) {
                emailAvailable = !existResult.emailMatch();
            }
            if (namePresent) {
                nameAvailable = !existResult.nameMatch();
            }
            if (domainPresent) {
                subdomainAvailable = !existResult.domainMatch();
            }
        }

        boolean reserved = domainPresent && RESERVED_SUBDOMAINS.contains(customDomain);
        if (reserved) {
            subdomainAvailable = false;
        }

        ServerAvailabilityResponse.Builder builder = ServerAvailabilityResponse.newBuilder()
            .setEmailAvailable(emailAvailable)
            .setNameAvailable(nameAvailable)
            .setSubdomainAvailable(subdomainAvailable);
        if (reserved) {
            builder.setMessage("This subdomain is reserved.");
        }
        return ResponseEntity.ok(builder.build());
    }
}
