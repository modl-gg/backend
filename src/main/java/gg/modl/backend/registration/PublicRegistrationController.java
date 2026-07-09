package gg.modl.backend.registration;

import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.beta.SubdomainValidator;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
@Profile("!staging")
public class PublicRegistrationController {
    private static final String CLI_TURNSTILE_TOKEN_HEADER = "X-Turnstile-Token";

    @Value("${modl.registration.cli-turnstile-required:false}")
    private boolean cliTurnstileRequired;

    private final ServerService serverService;
    private final SessionService sessionService;
    private final RegistrationService registrationService;
    private final CookieUtil cookieUtil;
    private final SubdomainValidator subdomainValidator;

    @PostMapping
    public ResponseEntity<?> register(
        HttpServletRequest request,
        @RequestBody PublicRegistrationRequest requestData) {

        RegistrationService.RegistrationCommand command = new RegistrationService.RegistrationCommand(
            RegistrationService.RegistrationChannel.WEB,
            true,
            requestData.getTurnstileToken(),
            RequestUtil.getClientIp(request),
            requestData.getEmail(),
            requestData.getServerName(),
            requestData.getCustomDomain()
        );

        return switch (registrationService.performRegistration(command)) {
            case RegistrationService.RegistrationOutcome.Rejected rejected -> toRejectionResponse(rejected);
            case RegistrationService.RegistrationOutcome.Created created -> ResponseEntity.status(201).body(
                RegistrationProtoMapper.toRegistrationResponse(
                    true,
                    "Registration successful. Please check your email to verify your account.",
                    created.server()
                ));
        };
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

        RegistrationService.RegistrationCommand command = new RegistrationService.RegistrationCommand(
            RegistrationService.RegistrationChannel.CLI,
            cliTurnstileRequired,
            request.getHeader(CLI_TURNSTILE_TOKEN_HEADER),
            RequestUtil.getClientIp(request),
            requestData.getEmail(),
            requestData.getServerName(),
            requestData.getCustomDomain()
        );

        return switch (registrationService.performRegistration(command)) {
            case RegistrationService.RegistrationOutcome.Rejected rejected -> toRejectionResponse(rejected);
            case RegistrationService.RegistrationOutcome.Created created -> {
                Server server = created.server();
                String cliSetupToken = registrationService.generateToken();
                serverService.setCliSetupToken(server, cliSetupToken);
                yield ResponseEntity.status(201).body(RegistrationProtoMapper.toCliRegistrationResponse(
                    true,
                    "Registration successful. Please check your email to verify your account.",
                    server,
                    cliSetupToken
                ));
            }
        };
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

    private ResponseEntity<?> toRejectionResponse(RegistrationService.RegistrationOutcome.Rejected rejected) {
        RegistrationService.RegistrationRejection rejection = rejected.rejection();
        return ResponseEntity.status(rejection.status())
            .body(RegistrationProtoMapper.toRegistrationResponse(false, rejection.message(), null));
    }

    @PostMapping("/check-availability")
    public ResponseEntity<?> checkAvailability(@RequestBody ServerAvailabilityRequest request) {
        String serverName = request.hasServerName() ? request.getServerName() : null;
        String customDomain = request.hasCustomDomain() ? request.getCustomDomain() : null;

        boolean namePresent = serverName != null && !serverName.isBlank();
        boolean domainPresent = customDomain != null && !customDomain.isBlank();
        String normalizedDomain = domainPresent ? subdomainValidator.normalize(customDomain) : "";

        boolean nameAvailable = true;
        boolean subdomainAvailable = true;

        if (namePresent || domainPresent) {
            ServerService.ServerExistResult existResult = serverService.doesServerExist(
                "",
                namePresent ? serverName : "",
                normalizedDomain
            );
            if (namePresent) {
                nameAvailable = !existResult.nameMatch();
            }
            if (domainPresent) {
                subdomainAvailable = !existResult.domainMatch();
            }
        }

        boolean reserved = domainPresent && subdomainValidator.isReserved(customDomain);
        if (reserved) {
            subdomainAvailable = false;
        }

        ServerAvailabilityResponse.Builder builder = ServerAvailabilityResponse.newBuilder()
            .setEmailAvailable(true)
            .setNameAvailable(nameAvailable)
            .setSubdomainAvailable(subdomainAvailable);
        if (reserved) {
            builder.setMessage("This subdomain is reserved.");
        }
        return ResponseEntity.ok(builder.build());
    }
}
