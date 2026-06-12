package gg.modl.backend.auth.controller;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.auth.AuthResponseMessage;
import gg.modl.backend.auth.AuthService;
import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffService;
import gg.modl.backend.infrastructure.util.CookieUtil;
import gg.modl.proto.modl.v1.PanelAuthResponse;
import gg.modl.proto.modl.v1.PanelPermissionsResponse;
import gg.modl.proto.modl.v1.PanelProfileResponse;
import gg.modl.proto.modl.v1.PanelSendEmailCodeRequest;
import gg.modl.proto.modl.v1.PanelSessionsResponse;
import gg.modl.proto.modl.v1.PanelUpdateEmailRequest;
import gg.modl.proto.modl.v1.PanelUpdateEmailWithCodeRequest;
import gg.modl.proto.modl.v1.PanelUpdateProfileRequest;
import gg.modl.proto.modl.v1.PanelVerifyEmailCodeRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final CookieUtil cookieUtil;

    @PostMapping("/send-email-code")
    public ResponseEntity<PanelAuthResponse> sendEmailCode(
        HttpServletRequest request,
        @RequestBody PanelSendEmailCodeRequest requestData) throws Exception {

        Server server = RequestUtil.getRequestServer(request);

        // Always return generic success to prevent email enumeration
        if (!permissionService.isAuthorizedEmail(server, requestData.getEmail())) {
            return ResponseEntity.ok(PanelAuthProtoMapper.toAuthResponse(true, AuthResponseMessage.VERIFICATION_CODE_SENT));
        }

        authService.sendUserLoginCode(server, requestData.getEmail());

        return ResponseEntity.ok(PanelAuthProtoMapper.toAuthResponse(true, AuthResponseMessage.VERIFICATION_CODE_SENT));
    }

    @PostMapping("/verify-email-code")
    public ResponseEntity<PanelAuthResponse> verifyEmailCode(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestBody PanelVerifyEmailCodeRequest requestData) {

        Server server = RequestUtil.getRequestServer(request);

        // Return same error as invalid code to prevent email enumeration
        if (!permissionService.isAuthorizedEmail(server, requestData.getEmail())) {
            return ResponseEntity.badRequest().body(PanelAuthProtoMapper.toAuthResponse(false, AuthResponseMessage.INVALID_CODE));
        }

        boolean valid = authService.verifyCode(server, requestData.getEmail(), requestData.getCode());

        if (!valid) {
            return ResponseEntity.badRequest().body(PanelAuthProtoMapper.toAuthResponse(false, AuthResponseMessage.INVALID_CODE));
        }

        String clientIp = RequestUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        AuthSessionData session = sessionService.createSession(server, requestData.getEmail(), clientIp, userAgent);

        response.addCookie(cookieUtil.createSessionCookie(session.getId()));

        return ResponseEntity.ok(PanelAuthProtoMapper.toAuthResponse(true, AuthResponseMessage.LOGIN_SUCCESS));
    }

    @PostMapping("/logout")
    public ResponseEntity<PanelAuthResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        Server server = RequestUtil.getRequestServer(request);
        Set<String> sessionIds = extractSessionIds(request);

        for (String sessionId : sessionIds) {
            sessionService.invalidateSession(server, sessionId);
        }

        for (Cookie cookie : cookieUtil.createExpiredSessionCookies()) {
            response.addCookie(cookie);
        }

        return ResponseEntity.ok(PanelAuthProtoMapper.toAuthResponse(true, AuthResponseMessage.LOGOUT_SUCCESS));
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
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @PatchMapping("/profile")
    public ResponseEntity<?> updateProfile(
        HttpServletRequest request,
        @RequestBody PanelUpdateProfileRequest requestData) {

        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(PanelAuthProtoMapper.toAuthResponse(false, "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, email);

        String username = requestData.hasUsername() ? requestData.getUsername() : null;
        String language = requestData.hasLanguage() ? requestData.getLanguage() : null;
        String dateFormat = requestData.hasDateFormat() ? requestData.getDateFormat() : null;

        Optional<Staff> result = staffService.updateOrCreateProfileUsername(server, email, username, isSuperAdmin, language, dateFormat);
        if (result.isEmpty()) {
            if (isSuperAdmin) {
                String resolvedUsername = username != null ? username : "Admin";
                String resolvedLanguage = language != null ? language : "en";
                String resolvedDateFormat = dateFormat != null ? dateFormat : "MM/DD/YYYY";
                return ResponseEntity.ok(PanelAuthProtoMapper.toProfileResponse(
                    null, email, resolvedUsername, "Super Admin", resolvedUsername, resolvedLanguage, resolvedDateFormat));
            }
            return ResponseEntity.status(404).body(PanelAuthProtoMapper.toAuthResponse(false, "Staff member not found"));
        }
        Staff staff = result.get();
        String role = isSuperAdmin ? "Super Admin" : permissionService.resolveRoleName(server, staff.getRoleId());
        String minecraftUsername = staff.getAssignedMinecraftUsername() != null
                                   ? staff.getAssignedMinecraftUsername()
                                   : staff.getUsername();
        return ResponseEntity.ok(PanelAuthProtoMapper.toProfileResponse(
            staff.getId(), staff.getEmail(), staff.getUsername(), role, minecraftUsername, staff.getLanguage(), staff.getDateFormat()));
    }

    @PostMapping("/email/send-code")
    public ResponseEntity<PanelAuthResponse> sendEmailChangeCode(
        HttpServletRequest request,
        @RequestBody PanelUpdateEmailRequest requestData) {

        String currentEmail = RequestUtil.getSessionEmail(request);
        if (currentEmail == null) {
            return ResponseEntity.status(401).body(PanelAuthProtoMapper.toAuthResponse(false, "Not authenticated"));
        }

        String newEmail = requestData.getNewEmail().trim();
        if (currentEmail.equalsIgnoreCase(newEmail)) {
            return ResponseEntity.badRequest().body(PanelAuthProtoMapper.toAuthResponse(false, "New email must be different from your current email."));
        }

        Server server = RequestUtil.getRequestServer(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, currentEmail);

        if (!isSuperAdmin && !permissionService.isAuthorizedEmail(server, newEmail)) {
            return ResponseEntity.badRequest().body(PanelAuthProtoMapper.toAuthResponse(false, "This email is not authorized for this panel."));
        }

        try {
            authService.sendUserLoginCode(server, newEmail);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(PanelAuthProtoMapper.toAuthResponse(false, "Failed to send verification code."));
        }

        return ResponseEntity.ok(PanelAuthProtoMapper.toAuthResponse(true, "Verification code sent to new email."));
    }

    @PatchMapping("/email")
    public ResponseEntity<PanelAuthResponse> updateEmail(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestBody PanelUpdateEmailWithCodeRequest requestData) {

        String currentEmail = RequestUtil.getSessionEmail(request);
        if (currentEmail == null) {
            return ResponseEntity.status(401).body(PanelAuthProtoMapper.toAuthResponse(false, "Not authenticated"));
        }

        String newEmail = requestData.getNewEmail().trim();
        if (currentEmail.equalsIgnoreCase(newEmail)) {
            return ResponseEntity.badRequest().body(PanelAuthProtoMapper.toAuthResponse(false, "New email must be different from your current email."));
        }

        Server server = RequestUtil.getRequestServer(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, currentEmail);

        if (!isSuperAdmin && !permissionService.isAuthorizedEmail(server, newEmail)) {
            return ResponseEntity.badRequest().body(PanelAuthProtoMapper.toAuthResponse(false, "This email is not authorized for this panel."));
        }

        if (!authService.verifyCode(server, newEmail, requestData.getCode())) {
            return ResponseEntity.badRequest().body(PanelAuthProtoMapper.toAuthResponse(false, "Invalid or expired verification code."));
        }

        Optional<Staff> result = staffService.updateEmail(server, currentEmail, newEmail, isSuperAdmin);
        if (result.isEmpty()) {
            return ResponseEntity.status(404).body(PanelAuthProtoMapper.toAuthResponse(false, "Staff member not found"));
        }

        sessionService.invalidateAllSessionsForEmail(server, currentEmail);
        AuthSessionData newSession = sessionService.createSession(server, newEmail, RequestUtil.getClientIp(request), request.getHeader("User-Agent"));
        response.addCookie(cookieUtil.createSessionCookie(newSession.getId()));

        return ResponseEntity.ok(PanelAuthProtoMapper.toAuthResponse(true, "Email updated successfully."));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(PanelAuthProtoMapper.toAuthResponse(false, "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, email);

        Optional<Staff> staffOpt = staffService.getStaffByEmail(server, email);

        if (staffOpt.isPresent()) {
            Staff staff = staffOpt.get();
            String role = isSuperAdmin ? "Super Admin" : permissionService.resolveRoleName(server, staff.getRoleId());
            // Include Minecraft username if assigned, fall back to panel username
            String minecraftUsername = staff.getAssignedMinecraftUsername() != null
                                       ? staff.getAssignedMinecraftUsername()
                                       : staff.getUsername();
            return ResponseEntity.ok(PanelAuthProtoMapper.toProfileResponse(
                staff.getId(), staff.getEmail(), staff.getUsername(), role, minecraftUsername, staff.getLanguage(), staff.getDateFormat()));
        }

        // Super Admin without a staff record - return default username
        if (isSuperAdmin) {
            return ResponseEntity.ok(PanelAuthProtoMapper.toProfileResponse(null, email, "Admin", "Super Admin", "Admin", "en", "MM/DD/YYYY"));
        }

        return ResponseEntity.status(404).body(PanelAuthProtoMapper.toAuthResponse(false, "Staff member not found"));
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions(HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(PanelAuthProtoMapper.toAuthResponse(false, "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        AuthSessionData currentSession = RequestUtil.getSession(request);
        String currentSessionId = currentSession != null ? currentSession.getId() : null;

        List<AuthSessionData> sessions = sessionService.findAllSessionsForEmail(server, email);

        return ResponseEntity.ok(PanelAuthProtoMapper.toSessionsResponse(sessions, currentSessionId));
    }

    @GetMapping("/permissions")
    public ResponseEntity<PanelPermissionsResponse> getUserPermissions(HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(PanelAuthProtoMapper.toPermissionsResponse(List.of()));
        }

        Server server = RequestUtil.getRequestServer(request);

        // Check if user is Super Admin (server admin)
        if (permissionService.isSuperAdmin(server, email)) {
            return ResponseEntity.ok(PanelAuthProtoMapper.toPermissionsResponse(permissionService.getAllPermissionIds(server)));
        }

        // Get staff member and their role
        Optional<Staff> staffOpt = staffService.getStaffByEmail(server, email);
        if (staffOpt.isEmpty()) {
            return ResponseEntity.ok(PanelAuthProtoMapper.toPermissionsResponse(List.of()));
        }

        String roleId = staffOpt.get().getRoleId();
        Optional<StaffRole> roleOpt = permissionService.getRoleById(server, roleId);

        return roleOpt.map(staffRole -> ResponseEntity.ok(PanelAuthProtoMapper.toPermissionsResponse(staffRole.getPermissions())))
            .orElseGet(() -> ResponseEntity.ok(PanelAuthProtoMapper.toPermissionsResponse(List.of())));
    }
}
