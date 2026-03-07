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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    @PostMapping("/send-email-code")
    public ResponseEntity<AuthResponse> sendEmailCode(
            HttpServletRequest request,
            @RequestBody @Valid SendEmailCodeRequest requestData) {

        Server server = RequestUtil.getRequestServer(request);

        // Always return generic success to prevent email enumeration
        if (!isAuthorizedEmail(server, requestData.email())) {
            return ResponseEntity.ok(new AuthResponse(true, AuthResponseMessage.VERIFICATION_CODE_SENT));
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
            @RequestBody @Valid VerifyCodeRequest requestData) {

        Server server = RequestUtil.getRequestServer(request);

        // Return same error as invalid code to prevent email enumeration
        if (!isAuthorizedEmail(server, requestData.email())) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, AuthResponseMessage.INVALID_CODE));
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
        Set<String> sessionIds = extractSessionIds(request);
        String sessionEmail = RequestUtil.getSessionEmail(request);

        for (String sessionId : sessionIds) {
            sessionService.invalidateSession(server, sessionId);
        }

        if (sessionEmail != null && !sessionEmail.isBlank()) {
            sessionService.invalidateAllSessionsForEmail(server, sessionEmail);
        }

        for (Cookie cookie : createExpiredSessionCookies()) {
            response.addCookie(cookie);
        }

        return ResponseEntity.ok(new AuthResponse(true, AuthResponseMessage.LOGOUT_SUCCESS));
    }

    @PatchMapping("/profile")
    public ResponseEntity<?> updateProfile(
            HttpServletRequest request,
            @RequestBody @Valid UpdateProfileRequest requestData) {

        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(new AuthResponse(false, "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, email);

        try {
            Optional<Staff> result = staffService.updateOrCreateProfileUsername(server, email, requestData.username(), isSuperAdmin, requestData.language(), requestData.dateFormat());
            if (result.isEmpty()) {
                return ResponseEntity.status(404).body(new AuthResponse(false, "Staff member not found"));
            }
            Staff staff = result.get();
            String role = isSuperAdmin ? "Super Admin" : staff.getRole();
            String minecraftUsername = staff.getAssignedMinecraftUsername() != null
                ? staff.getAssignedMinecraftUsername()
                : staff.getUsername();
            return ResponseEntity.ok(new ProfileResponse(staff.getId(), staff.getEmail(), staff.getUsername(), role, minecraftUsername, staff.getLanguage(), staff.getDateFormat()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, e.getMessage()));
        }
    }

    @PatchMapping("/email")
    public ResponseEntity<?> updateEmail(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody @Valid UpdateEmailRequest requestData) {

        String currentEmail = RequestUtil.getSessionEmail(request);
        if (currentEmail == null) {
            return ResponseEntity.status(401).body(new AuthResponse(false, "Not authenticated"));
        }

        String newEmail = requestData.newEmail().trim();
        if (currentEmail.equalsIgnoreCase(newEmail)) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, "New email must be different from your current email."));
        }

        Server server = RequestUtil.getRequestServer(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, currentEmail);

        try {
            Optional<Staff> result = staffService.updateEmail(server, currentEmail, newEmail, isSuperAdmin);
            if (result.isEmpty()) {
                return ResponseEntity.status(404).body(new AuthResponse(false, "Staff member not found"));
            }

            // Invalidate all sessions for the old email, then create a fresh one for the new email
            // so the user stays logged in without disruption.
            sessionService.invalidateAllSessionsForEmail(server, currentEmail);
            AuthSessionData newSession = sessionService.createSession(server, newEmail);
            response.addCookie(createSessionCookie(newSession.getId()));

            return ResponseEntity.ok(new AuthResponse(true, "Email updated successfully."));
        } catch (IllegalStateException | IllegalArgumentException e) {
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
            // Include Minecraft username if assigned, fall back to panel username
            String minecraftUsername = staff.getAssignedMinecraftUsername() != null
                ? staff.getAssignedMinecraftUsername()
                : staff.getUsername();
            return ResponseEntity.ok(new ProfileResponse(staff.getId(), staff.getEmail(), staff.getUsername(), role, minecraftUsername, staff.getLanguage(), staff.getDateFormat()));
        }

        // Super Admin without a staff record - return default username
        if (isSuperAdmin) {
            return ResponseEntity.ok(new ProfileResponse(null, email, "Admin", "Super Admin", "Admin", "en", "MM/DD/YYYY"));
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

        if (authConfiguration.isDevelopmentMode()) {
            cookie.setAttribute("SameSite", "Lax");
        } else {
            cookie.setAttribute("SameSite", "Strict");
        }

        return cookie;
    }

    private List<Cookie> createExpiredSessionCookies() {
        List<Cookie> cookies = new ArrayList<>();
        cookies.add(createExpiredSessionCookie(null));

        // Also expire any cookies set with the configured domain (for migration from old cookies)
        String configuredDomain = getConfiguredCookieDomain();
        if (configuredDomain != null) {
            cookies.add(createExpiredSessionCookie(configuredDomain));
            if (!configuredDomain.startsWith(".")) {
                cookies.add(createExpiredSessionCookie("." + configuredDomain));
            } else if (configuredDomain.length() > 1) {
                cookies.add(createExpiredSessionCookie(configuredDomain.substring(1)));
            }
        }

        return cookies;
    }

    private Cookie createExpiredSessionCookie(String domain) {
        Cookie cookie = new Cookie(authConfiguration.getSessionCookieName(), "");

        cookie.setHttpOnly(true);
        cookie.setSecure(authConfiguration.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(0);
        if (authConfiguration.isDevelopmentMode()) {
            cookie.setAttribute("SameSite", "Lax");
        } else {
            cookie.setAttribute("SameSite", "Strict");
        }
        if (domain != null) {
            cookie.setDomain(domain);
        }

        return cookie;
    }

    private Set<String> extractSessionIds(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Set.of();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> authConfiguration.getSessionCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }


    private String getConfiguredCookieDomain() {
        String cookieDomain = authConfiguration.getCookieDomain();
        if (cookieDomain == null || cookieDomain.isBlank()) {
            return null;
        }
        return cookieDomain;
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

    public record UpdateProfileRequest(String username, String language, String dateFormat) {}

    public record UpdateEmailRequest(@Email @NotBlank String newEmail) {}

    public record ProfileResponse(String id, String email, String username, String role, String minecraftUsername, String language, String dateFormat) {}
}
