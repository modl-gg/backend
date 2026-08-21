package gg.modl.backend.auth.controller;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.auth.AuthResponseMessage;
import gg.modl.backend.auth.AuthService;
import gg.modl.backend.auth.EmailChangeService;
import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.SupportedLanguages;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffLookupCache;
import gg.modl.backend.staff.service.StaffProfileService;
import gg.modl.backend.staff.service.SuperAdminStaffSynthesizer;
import gg.modl.backend.infrastructure.util.CookieUtil;
import gg.modl.proto.modl.v1.PanelAuthResponse;
import gg.modl.proto.modl.v1.PanelPermissionsResponse;
import gg.modl.proto.modl.v1.PanelSendEmailCodeRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final StaffProfileService staffProfileService;
    private final StaffLookupCache staffLookupCache;
    private final PermissionService permissionService;
    private final RoleAuthorization roleAuthorization;
    private final CookieUtil cookieUtil;
    private final EmailChangeService emailChangeService;

    @PostMapping("/send-email-code")
    public ResponseEntity<PanelAuthResponse> sendEmailCode(
        HttpServletRequest request,
        @RequestBody PanelSendEmailCodeRequest requestData) throws Exception {

        Server server = RequestUtil.getRequestServer(request);

        if (!permissionService.isAuthorizedEmail(server, requestData.getEmail())) {
            return verificationCodeSentResponse();
        }

        authService.sendUserLoginCode(server, requestData.getEmail());

        return verificationCodeSentResponse();
    }

    @PostMapping("/verify-email-code")
    public ResponseEntity<PanelAuthResponse> verifyEmailCode(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestBody PanelVerifyEmailCodeRequest requestData) {

        Server server = RequestUtil.getRequestServer(request);

        if (!permissionService.isAuthorizedEmail(server, requestData.getEmail())) {
            return invalidCodeResponse();
        }

        boolean valid = authService.verifyCode(server, requestData.getEmail(), requestData.getCode());

        if (!valid) {
            return invalidCodeResponse();
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

        expireSessionCookies(response);

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

        Optional<Staff> result = staffProfileService.updateOrCreateProfileUsername(server, email, username, isSuperAdmin, language, dateFormat);
        if (result.isEmpty()) {
            if (isSuperAdmin) {
                String resolvedUsername = username != null ? username : SuperAdminStaffSynthesizer.SUPER_ADMIN_USERNAME;
                String resolvedLanguage = language != null ? language : SupportedLanguages.DEFAULT;
                String resolvedDateFormat = dateFormat != null ? dateFormat : Staff.DEFAULT_DATE_FORMAT;
                return superAdminProfileResponse(email, resolvedUsername, resolvedLanguage, resolvedDateFormat);
            }
            return ResponseEntity.status(404).body(PanelAuthProtoMapper.toAuthResponse(false, "Staff member not found"));
        }
        Staff staff = result.get();
        String role = isSuperAdmin ? RoleAuthorization.SUPER_ADMIN_ROLE_NAME : permissionService.effectiveRoleName(server, staff);
        String minecraftUsername = minecraftUsernameOrPanel(staff);
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

        Server server = RequestUtil.getRequestServer(request);
        emailChangeService.sendChangeCode(server, currentEmail, requestData.getNewEmail());

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

        Server server = RequestUtil.getRequestServer(request);
        AuthSessionData newSession = emailChangeService.changeEmail(
            server, currentEmail, requestData.getNewEmail(), requestData.getCode(),
            RequestUtil.getClientIp(request), request.getHeader("User-Agent"));
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

        Optional<Staff> staffOpt = staffLookupCache.findByEmail(server, email);

        if (staffOpt.isPresent()) {
            Staff staff = staffOpt.get();
            String role = isSuperAdmin ? RoleAuthorization.SUPER_ADMIN_ROLE_NAME : permissionService.effectiveRoleName(server, staff);
            String minecraftUsername = minecraftUsernameOrPanel(staff);
            return ResponseEntity.ok(PanelAuthProtoMapper.toProfileResponse(
                staff.getId(), staff.getEmail(), staff.getUsername(), role, minecraftUsername, staff.getLanguage(), staff.getDateFormat()));
        }

        if (isSuperAdmin) {
            return superAdminProfileResponse(email, SuperAdminStaffSynthesizer.SUPER_ADMIN_USERNAME, SupportedLanguages.DEFAULT, Staff.DEFAULT_DATE_FORMAT);
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

    @DeleteMapping("/sessions/{publicId}")
    public ResponseEntity<PanelAuthResponse> revokeSession(
        HttpServletRequest request,
        HttpServletResponse response,
        @PathVariable String publicId) {

        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(PanelAuthProtoMapper.toAuthResponse(false, "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        Optional<AuthSessionData> target = sessionService.findSessionByPublicId(server, email, publicId);
        if (target.isEmpty()) {
            return ResponseEntity.status(404).body(PanelAuthProtoMapper.toAuthResponse(false, "Session not found"));
        }

        String sessionId = target.get().getId();
        sessionService.invalidateSession(server, sessionId);
        expireCookiesIfCurrentSession(request, response, sessionId);

        return ResponseEntity.ok(PanelAuthProtoMapper.toAuthResponse(true, AuthResponseMessage.LOGOUT_SUCCESS));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<PanelAuthResponse> revokeAllSessions(HttpServletRequest request, HttpServletResponse response) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(PanelAuthProtoMapper.toAuthResponse(false, "Not authenticated"));
        }

        Server server = RequestUtil.getRequestServer(request);
        sessionService.invalidateAllSessionsForEmail(server, email);
        expireSessionCookies(response);

        return ResponseEntity.ok(PanelAuthProtoMapper.toAuthResponse(true, AuthResponseMessage.LOGOUT_SUCCESS));
    }

    private void expireCookiesIfCurrentSession(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        AuthSessionData currentSession = RequestUtil.getSession(request);
        if (currentSession != null && sessionId.equals(currentSession.getId())) {
            expireSessionCookies(response);
        }
    }

    private void expireSessionCookies(HttpServletResponse response) {
        for (Cookie cookie : cookieUtil.createExpiredSessionCookies()) {
            response.addCookie(cookie);
        }
    }

    private ResponseEntity<PanelAuthResponse> verificationCodeSentResponse() {
        return ResponseEntity.ok(PanelAuthProtoMapper.toAuthResponse(true, AuthResponseMessage.VERIFICATION_CODE_SENT));
    }

    private ResponseEntity<PanelAuthResponse> invalidCodeResponse() {
        return ResponseEntity.badRequest().body(PanelAuthProtoMapper.toAuthResponse(false, AuthResponseMessage.INVALID_CODE));
    }

    private ResponseEntity<?> superAdminProfileResponse(String email, String username, String language, String dateFormat) {
        return ResponseEntity.ok(PanelAuthProtoMapper.toProfileResponse(
            null, email, username, RoleAuthorization.SUPER_ADMIN_ROLE_NAME, username, language, dateFormat));
    }

    private static String minecraftUsernameOrPanel(Staff staff) {
        return staff.getAssignedMinecraftUsername() != null
            ? staff.getAssignedMinecraftUsername()
            : staff.getUsername();
    }

    @GetMapping("/permissions")
    public ResponseEntity<PanelPermissionsResponse> getUserPermissions(HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(PanelAuthProtoMapper.toPermissionsResponse(List.of()));
        }

        Server server = RequestUtil.getRequestServer(request);
        List<String> permissions = roleAuthorization.effectivePermissionIds(server, roleAuthorization.panelPerformer(server, email));

        return ResponseEntity.ok(PanelAuthProtoMapper.toPermissionsResponse(permissions));
    }
}
