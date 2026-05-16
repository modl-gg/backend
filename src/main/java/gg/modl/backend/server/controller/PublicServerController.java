package gg.modl.backend.server.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
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
        "docs", "secure", "alpha", "cdn",
        "nexus", "replay", "replays"
    );

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(new RegisterResponse(false, "Use /v1/public/registration instead."));
    }

    @PostMapping("/check-availability")
    public ResponseEntity<AvailabilityResponse> checkAvailability(@RequestBody @Valid AvailabilityRequest request) {
        return ResponseEntity.status(HttpStatus.GONE)
            .body(new AvailabilityResponse(false, false, false, "Use /v1/public/registration/check-availability instead."));
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
