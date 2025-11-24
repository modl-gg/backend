package gg.modl.backend.server.controller;

import gg.modl.backend.rest.RESTMapping;
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

@RestController
@RequestMapping(RESTMapping.V1_PUBLIC_SERVERS)
@RequiredArgsConstructor
public class PublicServerController {
    private final ServerService serverService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody @Valid RegisterRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            // Invalid schema
            return ResponseEntity.badRequest().body(new RegisterResponse(false, ServerResponseMessage.REGISTER_INVALID_SCHEMA));
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
