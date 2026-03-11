package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.AdminUser;
import gg.modl.backend.admin.service.AdminAuthService;
import gg.modl.backend.auth.AuthService;
import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_AUTH)
@RequiredArgsConstructor
@Slf4j
public class AdminAuthController {
    private final AdminAuthService adminAuthService;
    private final AuthService authService;
    private final SessionService sessionService;
    private final CookieUtil cookieUtil;
    private static final String ADMIN_SESSION_COOKIE = "modl.admin.session";
    private static final long SESSION_MAX_AGE = 24 * 60 * 60; // 24 hours

    @PostMapping("/request-code")
    public ResponseEntity<?> requestCode(@RequestBody @Valid RequestCodeRequest request) {

        Optional<AdminUser> adminOpt = adminAuthService.findByEmail(request.email());
        if (adminOpt.isPresent()) {
            try {
                authService.sendAdminLoginCode(request.email());
            } catch (Exception e) {
                log.error("Failed to send verification code", e);
            }
        }

        return ResponseEntity.ok(new ApiResponse(true, "If this email is registered, a verification code has been sent"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestBody @Valid LoginRequest loginRequest) {

        // Always verify code regardless of user existence to prevent timing-based enumeration
        boolean codeValid = authService.verifyAdminCode(loginRequest.email(), loginRequest.code());
        Optional<AdminUser> adminOpt = adminAuthService.findByEmail(loginRequest.email());

        if (adminOpt.isEmpty() || !codeValid) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Invalid credentials"));
        }

        AdminUser admin = adminOpt.get();
        String clientIp = RequestUtil.getClientIp(request);
        adminAuthService.updateLastActivity(admin.getEmail(), clientIp);

        AuthSessionData session = sessionService.createAdminSession(admin.getEmail());

        response.addCookie(cookieUtil.createSessionCookie(ADMIN_SESSION_COOKIE, session.getId(), SESSION_MAX_AGE));

        return ResponseEntity.ok(new LoginResponse(true, "Login successful",
            new UserData(admin.getEmail(), admin.getLastActivityAt())));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        Set<String> sessionEmails = new LinkedHashSet<>();

        for (String sessionId : extractSessionIds(request)) {
            sessionService.findValidAdminSession(sessionId)
                .map(AuthSessionData::getEmail)
                .ifPresent(sessionEmails::add);
            sessionService.invalidateAdminSession(sessionId);
        }

        for (String sessionEmail : sessionEmails) {
            sessionService.invalidateAllAdminSessionsForEmail(sessionEmail);
        }

        for (Cookie expiredCookie : cookieUtil.createExpiredSessionCookies(ADMIN_SESSION_COOKIE)) {
            response.addCookie(expiredCookie);
        }

        return ResponseEntity.ok(new ApiResponse(true, "Logout successful"));
    }

    private Set<String> extractSessionIds(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Set.of();
        }

        return Arrays.stream(cookies)
            .filter(cookie -> ADMIN_SESSION_COOKIE.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && !value.isBlank())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @GetMapping("/session")
    public ResponseEntity<?> getSession(HttpServletRequest request) {
        String sessionId = extractSessionId(request);
        if (sessionId == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Not authenticated"));
        }

        Optional<AuthSessionData> sessionOpt = sessionService.findAndRefreshAdminSession(sessionId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Session expired"));
        }
        AuthSessionData session = sessionOpt.get();

        Optional<AdminUser> adminOpt = adminAuthService.findByEmail(session.getEmail());
        if (adminOpt.isEmpty()) {
            sessionService.invalidateAdminSession(sessionId);
            return ResponseEntity.status(401).body(new ApiResponse(false, "User not found"));
        }

        AdminUser admin = adminOpt.get();
        return ResponseEntity.ok(new SessionResponse(true,
            new SessionData(admin.getEmail(), admin.getLastActivityAt(), admin.getLoggedInIps(), true)));
    }

    private String extractSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ADMIN_SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    // Helper to check if request is authenticated (for use by other admin controllers)
    public Optional<AdminSession> getAuthenticatedSession(HttpServletRequest request) {
        String sessionId = extractSessionId(request);
        if (sessionId == null) {
            return Optional.empty();
        }

        Optional<AuthSessionData> sessionOpt = sessionService.findAndRefreshAdminSession(sessionId);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        AuthSessionData session = sessionOpt.get();
        return adminAuthService.findByEmail(session.getEmail())
            .map(admin -> new AdminSession(admin.getId(), session.getEmail(), session.getCreatedAt()));
    }

    // Request/Response records
    public record RequestCodeRequest(@Email @NotBlank String email) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String code) {}

    public record ApiResponse(boolean success, String message) {}

    public record LoginResponse(boolean success, String message, UserData data) {}

    public record UserData(String email, java.util.Date lastActivityAt) {}

    public record SessionResponse(boolean success, SessionData data) {}

    public record SessionData(String email, java.util.Date lastActivityAt, java.util.List<String> loggedInIps, boolean isAuthenticated) {}

    public record AdminSession(String adminId, String email, Date createdAt) {}
}
