package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.AdminUser;
import gg.modl.backend.admin.service.AdminAuthService;
import gg.modl.backend.rest.RESTMappingV1;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_AUTH)
@RequiredArgsConstructor
@Slf4j
public class AdminAuthController {
    private static final String ADMIN_SESSION_COOKIE = "modl.admin.session";
    private static final long SESSION_MAX_AGE = 24 * 60 * 60; // 24 hours
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int SESSION_TOKEN_BYTES = 32;

    private final AdminAuthService adminAuthService;

    @Value("${modl.cookie-domain:}")
    private String cookieDomain;

    @Value("${modl.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${modl.development-mode:false}")
    private boolean developmentMode;

    // Simple in-memory session store (in production, use Redis or database)
    private final Map<String, AdminSession> sessions = new ConcurrentHashMap<>();

    @PostMapping("/request-code")
    public ResponseEntity<?> requestCode(
            @RequestBody @Valid RequestCodeRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Invalid email format"));
        }

        Optional<AdminUser> adminOpt = adminAuthService.findByEmail(request.email());
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Invalid email address"));
        }

        try {
            adminAuthService.sendVerificationCode(request.email());
            return ResponseEntity.ok(new ApiResponse(true, "Verification code sent to your email"));
        } catch (Exception e) {
            log.error("Failed to send verification code", e);
            return ResponseEntity.status(500).body(new ApiResponse(false, "Failed to send verification code"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody @Valid LoginRequest loginRequest,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Email and code are required"));
        }

        Optional<AdminUser> adminOpt = adminAuthService.findByEmail(loginRequest.email());
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Invalid credentials"));
        }

        if (!adminAuthService.verifyCode(loginRequest.email(), loginRequest.code())) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Invalid or expired code"));
        }

        AdminUser admin = adminOpt.get();
        String clientIp = getClientIp(request);
        adminAuthService.updateLastActivity(admin.getEmail(), clientIp);

        // Create session with cryptographically secure token
        String sessionId = generateSecureToken();
        sessions.put(sessionId, new AdminSession(admin.getId(), admin.getEmail()));

        // Set session cookie with security attributes
        Cookie sessionCookie = new Cookie(ADMIN_SESSION_COOKIE, sessionId);
        sessionCookie.setHttpOnly(true);
        sessionCookie.setSecure(cookieSecure);
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge((int) SESSION_MAX_AGE);
        sessionCookie.setAttribute("SameSite", developmentMode ? "Lax" : "Strict");
        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            sessionCookie.setDomain(cookieDomain);
        }
        response.addCookie(sessionCookie);

        return ResponseEntity.ok(new LoginResponse(true, "Login successful",
                new UserData(admin.getEmail(), admin.getLastActivityAt())));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = extractSessionId(request);
        if (sessionId != null) {
            sessions.remove(sessionId);
        }

        Cookie expiredCookie = new Cookie(ADMIN_SESSION_COOKIE, "");
        expiredCookie.setHttpOnly(true);
        expiredCookie.setSecure(cookieSecure);
        expiredCookie.setPath("/");
        expiredCookie.setMaxAge(0);
        expiredCookie.setAttribute("SameSite", developmentMode ? "Lax" : "Strict");
        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            expiredCookie.setDomain(cookieDomain);
        }
        response.addCookie(expiredCookie);

        return ResponseEntity.ok(new ApiResponse(true, "Logout successful"));
    }

    @GetMapping("/session")
    public ResponseEntity<?> getSession(HttpServletRequest request) {
        String sessionId = extractSessionId(request);
        if (sessionId == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Not authenticated"));
        }

        AdminSession session = sessions.get(sessionId);
        if (session == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Session expired"));
        }

        Optional<AdminUser> adminOpt = adminAuthService.findByEmail(session.email());
        if (adminOpt.isEmpty()) {
            sessions.remove(sessionId);
            return ResponseEntity.status(401).body(new ApiResponse(false, "User not found"));
        }

        AdminUser admin = adminOpt.get();
        return ResponseEntity.ok(new SessionResponse(true,
                new SessionData(admin.getEmail(), admin.getLastActivityAt(), admin.getLoggedInIps(), true)));
    }

    // Helper to check if request is authenticated (for use by other admin controllers)
    public Optional<AdminSession> getAuthenticatedSession(HttpServletRequest request) {
        String sessionId = extractSessionId(request);
        if (sessionId == null) return Optional.empty();
        return Optional.ofNullable(sessions.get(sessionId));
    }

    private String extractSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (ADMIN_SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[SESSION_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Request/Response records
    public record RequestCodeRequest(@Email @NotBlank String email) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String code) {}
    public record ApiResponse(boolean success, String message) {}
    public record LoginResponse(boolean success, String message, UserData data) {}
    public record UserData(String email, java.util.Date lastActivityAt) {}
    public record SessionResponse(boolean success, SessionData data) {}
    public record SessionData(String email, java.util.Date lastActivityAt, java.util.List<String> loggedInIps, boolean isAuthenticated) {}
    public record AdminSession(String adminId, String email) {}
}
