package gg.modl.backend.auth.controller;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.auth.WebAuthnService;
import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.service.StaffService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import gg.modl.backend.rest.RequestAttribute;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_AUTH + "/webauthn")
@RequiredArgsConstructor
@Slf4j
public class WebAuthnController {
    private final WebAuthnService webAuthnService;
    private final SessionService sessionService;
    private final AuthConfiguration authConfiguration;
    private final StaffService staffService;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    // --- Registration (requires session) ---

    @PostMapping("/register/options")
    public ResponseEntity<?> registerOptions(HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        try {
            WebAuthnService.StartRegistrationResult result = webAuthnService.startRegistration(server, getRequestDomain(request), email);
            Object options = objectMapper.readValue(result.optionsJson(), Object.class);
            return ResponseEntity.ok(Map.of("challengeId", result.challengeId(), "options", options));
        } catch (Exception e) {
            log.error("Failed to start WebAuthn registration for {}", email, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start registration"));
        }
    }

    @PostMapping("/register/verify")
    public ResponseEntity<?> registerVerify(
            HttpServletRequest request,
            @RequestBody @Valid RegisterVerifyRequest body) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        try {
            webAuthnService.finishRegistration(server, getRequestDomain(request), email, body.challengeId(), body.response(), body.name());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to complete WebAuthn registration for {}", email, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Registration failed"));
        }
    }

    // --- Credential management (requires session) ---

    @GetMapping("/credentials")
    public ResponseEntity<?> listCredentials(HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        List<WebAuthnService.CredentialInfo> credentials = webAuthnService.listCredentials(server, email);
        return ResponseEntity.ok(credentials);
    }

    @PatchMapping("/credentials/{id}")
    public ResponseEntity<?> renameCredential(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestBody @Valid RenameCredentialRequest body) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        boolean updated = webAuthnService.renameCredential(server, email, id, body.name());
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<?> deleteCredential(
            HttpServletRequest request,
            @PathVariable String id) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        boolean deleted = webAuthnService.deleteCredential(server, email, id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // --- Login (no session required) ---

    @PostMapping("/login/start")
    public ResponseEntity<?> loginStart(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        try {
            WebAuthnService.StartAuthenticationResult result = webAuthnService.startDiscoverableAuthentication(server, getRequestDomain(request));
            Object options = objectMapper.readValue(result.optionsJson(), Object.class);
            return ResponseEntity.ok(Map.of("challengeId", result.challengeId(), "options", options));
        } catch (Exception e) {
            log.error("Failed to start discoverable WebAuthn authentication", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start authentication"));
        }
    }

    @PostMapping("/login/options")
    public ResponseEntity<?> loginOptions(
            HttpServletRequest request,
            @RequestBody @Valid LoginOptionsRequest body) {
        Server server = RequestUtil.getRequestServer(request);

        // Prevent email enumeration: check if email is authorized first
        if (!isAuthorizedEmail(server, body.email())) {
            return ResponseEntity.ok(Map.of("hasPasskeys", false));
        }

        boolean hasPasskeys = webAuthnService.checkHasPasskeys(server, body.email());
        if (!hasPasskeys) {
            return ResponseEntity.ok(Map.of("hasPasskeys", false));
        }

        try {
            WebAuthnService.StartAuthenticationResult result = webAuthnService.startAuthentication(server, getRequestDomain(request), body.email());
            Object options = objectMapper.readValue(result.optionsJson(), Object.class);
            return ResponseEntity.ok(Map.of(
                    "hasPasskeys", true,
                    "challengeId", result.challengeId(),
                    "options", options
            ));
        } catch (Exception e) {
            log.error("Failed to start WebAuthn authentication for {}", body.email(), e);
            return ResponseEntity.ok(Map.of("hasPasskeys", false));
        }
    }

    @PostMapping("/login/verify")
    public ResponseEntity<?> loginVerify(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody @Valid LoginVerifyRequest body) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            String email = webAuthnService.finishAuthentication(server, getRequestDomain(request), body.challengeId(), body.response());

            // Verify this email is still authorized
            if (!isAuthorizedEmail(server, email)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Not authorized"));
            }

            // Create session (same as email code login)
            AuthSessionData session = sessionService.createSession(server, email);
            Cookie sessionCookie = createSessionCookie(session.getId());
            response.addCookie(sessionCookie);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to complete WebAuthn authentication", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Authentication failed"));
        }
    }

    // --- Helpers ---

    private String getRequestDomain(HttpServletRequest request) {
        return (String) request.getAttribute(RequestAttribute.SERVER_DOMAIN);
    }

    private boolean isAuthorizedEmail(Server server, String email) {
        if (permissionService.isSuperAdmin(server, email)) {
            return true;
        }
        return staffService.getStaffByEmail(server, email).isPresent();
    }

    private Cookie createSessionCookie(String sessionId) {
        Cookie cookie = new Cookie(authConfiguration.getSessionCookieName(), sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(authConfiguration.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge((int) authConfiguration.getSessionDurationSeconds());
        if (authConfiguration.isDevelopmentMode()) {
            cookie.setAttribute("SameSite", "Lax");
        } else {
            cookie.setAttribute("SameSite", "Strict");
        }
        return cookie;
    }

    // --- Request DTOs ---

    public record RegisterVerifyRequest(@NotBlank String challengeId, @NotBlank String response, String name) {}
    public record RenameCredentialRequest(@NotBlank String name) {}
    public record LoginOptionsRequest(@Email @NotBlank String email) {}
    public record LoginVerifyRequest(@NotBlank String challengeId, @NotBlank String response) {}
}
