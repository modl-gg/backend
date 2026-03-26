package gg.modl.backend.server.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.ServerResponseMessage;
import gg.modl.backend.server.ServerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_SERVER)
@RequiredArgsConstructor
public class PublicServerController {
    private final ServerService serverService;
    private static final Set<String> RESERVED_SUBDOMAINS = Set.of(
        "payments", "payment", "api", "app",
        "status", "mail", "www", "discord",
        "admin", "twitter", "demo", "panel",
        "ftp", "sftp", "www2", "www3",
        "billing", "stripe", "test", "staging",
        "root", "internal", "administrator", "mod",
        "beta", "dev", "portal", "dashboard",
        "modl", "support", "help", "email",
        "docs", "secure", "alpha", "cdn"
    );

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody @Valid RegisterRequest request) {
        if (RESERVED_SUBDOMAINS.contains(request.customDomain)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new RegisterResponse(false, ServerResponseMessage.REGISTER_RESERVED_SUBDOMAIN));
        }
        // TODO: ratelimit
        // TODO: cloudflare turnstile
        ServerService.ServerExistResult existResult = serverService.doesServerExist(request.email, request.serverName, request.customDomain);
        if (existResult.emailMatch()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new RegisterResponse(false, ServerResponseMessage.REGISTER_EMAIL_EXISTS));
        }
        if (existResult.nameMatch()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new RegisterResponse(false, ServerResponseMessage.REGISTER_NAME_EXISTS));
        }
        if (existResult.domainMatch()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new RegisterResponse(false, ServerResponseMessage.REGISTER_DOMAIN_EXISTS));
        }

        serverService.createServer(request.serverName, request.customDomain, request.email);

        return ResponseEntity.ok(new RegisterResponse(true, ServerResponseMessage.REGISTER_SUCCESS));
    }

    @PostMapping("/check-availability")
    public ResponseEntity<AvailabilityResponse> checkAvailability(@RequestBody @Valid AvailabilityRequest request) {
        boolean emailAvailable = true;
        boolean nameAvailable = true;
        boolean subdomainAvailable = true;
        String message = null;

        if (request.customDomain != null && !request.customDomain.isBlank() && RESERVED_SUBDOMAINS.contains(request.customDomain)) {
            subdomainAvailable = false;
            message = ServerResponseMessage.REGISTER_RESERVED_SUBDOMAIN;
        }

        ServerService.ServerExistResult existResult = serverService.doesServerExist(
                request.email != null ? request.email : "",
                request.serverName != null ? request.serverName : "",
                request.customDomain != null ? request.customDomain : "");

        if (existResult.emailMatch()) {
            emailAvailable = false;
            if (message == null) message = ServerResponseMessage.REGISTER_EMAIL_EXISTS;
        }
        if (existResult.nameMatch()) {
            nameAvailable = false;
            if (message == null) message = ServerResponseMessage.REGISTER_NAME_EXISTS;
        }
        if (existResult.domainMatch()) {
            subdomainAvailable = false;
            if (message == null) message = ServerResponseMessage.REGISTER_DOMAIN_EXISTS;
        }

        return ResponseEntity.ok(new AvailabilityResponse(emailAvailable, nameAvailable, subdomainAvailable, message));
    }

    public record AvailabilityRequest(
            @Size(max = 254) String email,
            @Size(max = 100) String serverName,
            @Size(max = 50) String customDomain) {}

    public record AvailabilityResponse(boolean emailAvailable, boolean nameAvailable, boolean subdomainAvailable, String message) {}

    public record RegisterRequest(@Email @NotBlank @Size(max = 254) String email,
                                  @Size(min = 3, max = 50) @NotBlank @Pattern(regexp = "^[a-zA-Z0-9 -]+$") String serverName,
                                  @Size(min = 3, max = 20) @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") String customDomain,
                                  @NotBlank @Size(max = 4096) String turnstileToken) {}

    public record RegisterResponse(boolean success, String message) {}
}
