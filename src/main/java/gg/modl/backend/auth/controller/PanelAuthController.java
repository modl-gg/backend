package gg.modl.backend.auth.controller;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.auth.AuthResponseMessage;
import gg.modl.backend.auth.AuthService;
import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping(RESTMappingV1.PANEL_AUTH)
@RequiredArgsConstructor
@Slf4j
public class PanelAuthController {
    private final AuthService authService;
    private final SessionService sessionService;
    private final AuthConfiguration authConfiguration;
    private final StaffService staffService;
    private final PermissionService permissionService;
    private Optional<Staff> result;

    @PostMapping("/send-email-code")
    public ResponseEntity<AuthResponse> sendEmailCode(
            HttpServletRequest request,
            @RequestBody @Valid SendEmailCodeRequest requestData,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, AuthResponseMessage.MISSING_EMAIL));
        }

        Server server = RequestUtil.getRequestServer(request);

        if (!isAuthorizedEmail(server, requestData.email())) {
            return ResponseEntity.status(403).body(new AuthResponse(false, AuthResponseMessage.UNAUTHORIZED_EMAIL));
        }

        try {
            authService.sendUserLoginCode(server, requestData.email());
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send login code email to {}", requestData.email(), e);
            return ResponseEntity.internalServerError()
                    .body(new AuthResponse(false, AuthResponseMessage.EMAIL_SEND_ERROR));
        }

        return ResponseEntity.ok(new AuthResponse(true, AuthResponseMessage.VERIFICATION_CODE_SENT));
    }

    @PostMapping("/verify-email-code")
    public ResponseEntity<AuthResponse> verifyEmailCode(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody @Valid VerifyCodeRequest requestData,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, AuthResponseMessage.MISSING_CODE));
        }

        Server server = RequestUtil.getRequestServer(request);

        if (!isAuthorizedEmail(server, requestData.email())) {
            return ResponseEntity.status(403).body(new AuthResponse(false, AuthResponseMessage.UNAUTHORIZED_EMAIL));
        }

        boolean valid = authService.verifyCode(server, requestData.email(), requestData.code());

        if (!valid) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, AuthResponseMessage.INVALID_CODE));
        }

        AuthSessionData session = sessionService.createSession(server, requestData.email());

        Cookie sessionCookie = createSessionCookie(session.getId());
        response.addCookie(sessionCookie);

        return ResponseEntity.ok(new AuthResponse(true, AuthResponseMessage.LOGIN_SUCCESS));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        Server server = RequestUtil.getRequestServer(request);
        String sessionId = extractSessionId(request);

        if (sessionId != null) {
            sessionService.invalidateSession(server, sessionId);
        }

        Cookie expiredCookie = createExpiredSessionCookie();
        response.addCookie(expiredCookie);

        return ResponseEntity.ok(new AuthResponse(true, AuthResponseMessage.LOGOUT_SUCCESS));
    }

    @PatchMapping("/profile")
    public ResponseEntity<?> updateProfile(
            HttpServletRequest request,
            @RequestBody @Valid UpdateProfileRequest requestData,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, "Invalid request data"));
        }

        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(new AuthResponse(false, "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, email);

        try {
            Optional<Staff> result = staffService.updateOrCreateProfileUsername(server, email, requestData.username(), isSuperAdmin);
            if (result.isEmpty()) {
                return ResponseEntity.status(404).body(new AuthResponse(false, "Staff member not found"));
            }
            Staff staff = result.get();
            String role = isSuperAdmin ? "Super Admin" : staff.getRole();
            return ResponseEntity.ok(new ProfileResponse(staff.getId(), staff.getEmail(), staff.getUsername(), role));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(new AuthResponse(false, "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, email);

        Optional<Staff> staffOpt = staffService.getStaffByEmail(server, email);

        if (staffOpt.isPresent()) {
            Staff staff = staffOpt.get();
            String role = isSuperAdmin ? "Super Admin" : staff.getRole();
            return ResponseEntity.ok(new ProfileResponse(staff.getId(), staff.getEmail(), staff.getUsername(), role));
        }

        // Super Admin without a staff record - return default username
        if (isSuperAdmin) {
            return ResponseEntity.ok(new ProfileResponse(null, email, "Admin", "Super Admin"));
        }

        return ResponseEntity.status(404).body(new AuthResponse(false, "Staff member not found"));
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<String>> getUserPermissions(HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Collections.emptyList());
        }

        Server server = RequestUtil.getRequestServer(request);

        // Check if user is Super Admin (server admin)
        if (permissionService.isSuperAdmin(server, email)) {
            return ResponseEntity.ok(permissionService.getAllPermissionIds(server));
        }

        // Get staff member and their role
        Optional<Staff> staffOpt = staffService.getStaffByEmail(server, email);
        if (staffOpt.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        String roleName = staffOpt.get().getRole();
        Optional<StaffRole> roleOpt = permissionService.getRoleByName(server, roleName);

        return roleOpt.map(staffRole -> ResponseEntity.ok(staffRole.getPermissions()))
            .orElseGet(() -> ResponseEntity.ok(Collections.emptyList()));
    }

    private Cookie createSessionCookie(String sessionId) {
        Cookie cookie = new Cookie(authConfiguration.getSessionCookieName(), sessionId);

        cookie.setHttpOnly(true);
        cookie.setSecure(authConfiguration.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge((int) authConfiguration.getSessionDurationSeconds());
        cookie.setAttribute("SameSite", authConfiguration.isDevelopmentMode() ? "Lax" : "Strict");

        return cookie;
    }

    private Cookie createExpiredSessionCookie() {
        Cookie cookie = new Cookie(authConfiguration.getSessionCookieName(), "");

        cookie.setHttpOnly(true);
        cookie.setSecure(authConfiguration.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", authConfiguration.isDevelopmentMode() ? "Lax" : "Strict");

        return cookie;
    }

    private String extractSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> authConfiguration.getSessionCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean isAuthorizedEmail(Server server, String email) {
        if (permissionService.isSuperAdmin(server, email)) {
            return true;
        }
        return staffService.getStaffByEmail(server, email).isPresent();
    }

    public record AuthResponse(boolean success, String message) {}

    public record SendEmailCodeRequest(@Email @NotBlank String email) {}

    public record VerifyCodeRequest(@Email @NotBlank String email, @NotBlank String code) {}

    public record UpdateProfileRequest(String username) {}

    public record ProfileResponse(String id, String email, String username, String role) {}
}
