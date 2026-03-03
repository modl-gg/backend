package gg.modl.backend.rest;

import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.Objects;

public final class RequestUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final boolean TRUST_PROXY_HEADERS = Boolean.parseBoolean(
            Optional.ofNullable(System.getProperty("modl.trust-proxy-headers"))
                    .orElseGet(() -> Optional.ofNullable(System.getenv("MODL_TRUST_PROXY_HEADERS")).orElse("false"))
    );

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
        String remoteAddr = request.getRemoteAddr();
        boolean shouldTrustProxyHeaders = TRUST_PROXY_HEADERS || isLikelyTrustedProxy(remoteAddr);
        if (shouldTrustProxyHeaders) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                String firstHop = xForwardedFor.split(",")[0].trim();
                if (!firstHop.isEmpty()) {
                    return firstHop;
                }
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
        }
        return remoteAddr;
    }

    public static String generateSecureToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean isLikelyTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress();
        } catch (Exception ignored) {
            return false;
        }
    }
}
