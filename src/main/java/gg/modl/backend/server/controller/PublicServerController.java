package gg.modl.backend.server.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.ServerResponseMessage;
import gg.modl.backend.server.ServerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_SERVER)
@RequiredArgsConstructor
public class PublicServerController {
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
    private final ServerService serverService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody @Valid RegisterRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            // Invalid schema
            return ResponseEntity.badRequest().body(new RegisterResponse(false, ServerResponseMessage.REGISTER_INVALID_SCHEMA));
        }
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

    public record RegisterRequest(@Email @NotBlank String email,
                                  @Size(min = 3, max = 50) @NotBlank @Pattern(regexp = "^[a-zA-Z0-9 -]+$") String serverName,
                                  @Size(min = 3, max = 20) @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") String customDomain,
                                  @NotBlank String turnstileToken) {}
    public record RegisterResponse(boolean success, String message) {}
}
