package gg.modl.backend.rest;

import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public final class RequestUtil {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @NotNull
    public static Server getRequestServer(HttpServletRequest request) {
        return Objects.requireNonNull((Server) request.getAttribute(RequestAttribute.SERVER), "Server should not be null if being called from panel route!");
    }

    @Nullable
    public static AuthSessionData getSession(HttpServletRequest request) {
        return (AuthSessionData) request.getAttribute(RequestAttribute.SESSION);
    }

    @Nullable
    public static String getSessionEmail(HttpServletRequest request) {
        AuthSessionData session = getSession(request);
        return session != null ? session.getEmail() : null;
    }

    /**
     * Get the current username from the session.
     * Falls back to email if username lookup fails.
     */
    @NotNull
    public static String getCurrentUsername(HttpServletRequest request) {
        AuthSessionData session = getSession(request);
        if (session == null || session.getEmail() == null) {
            return "Unknown";
        }
        // Use email as username fallback - the service layer should resolve actual username if needed
        return session.getEmail();
    }

    public static String getClientIp(HttpServletRequest request) {
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

    public static String generateSecureToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
