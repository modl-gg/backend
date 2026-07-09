package gg.modl.backend.infrastructure.rest;

import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RequestUtil {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RequestUtil.class);
    private static volatile boolean warnedAboutProxy = false;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final boolean TRUST_PROXY_HEADERS = resolveTrustProxyHeaders();
    private static final String CLIENT_IP_HEADER = resolveClientIpHeaderName();
    private static final int TRUSTED_PROXY_COUNT = resolveTrustedProxyCount();
    private static final Pattern IPV6_LITERAL_CHARS = Pattern.compile("[0-9A-Fa-f:.%]+");

    private static boolean resolveTrustProxyHeaders() {
        String value = System.getProperty("modl.trust-proxy-headers");
        if (value == null) {
            value = System.getProperty("MODL_TRUST_PROXY_HEADERS");
        }
        if (value == null) {
            value = System.getenv("MODL_TRUST_PROXY_HEADERS");
        }
        return Boolean.parseBoolean(value);
    }

    private static String resolveClientIpHeaderName() {
        String value = System.getProperty("modl.client-ip-header");
        if (value == null) {
            value = System.getProperty("MODL_CLIENT_IP_HEADER");
        }
        if (value == null) {
            value = System.getenv("MODL_CLIENT_IP_HEADER");
        }
        if (value == null || value.isBlank()) {
            return "CF-Connecting-IP";
        }
        return value.trim();
    }

    private static int resolveTrustedProxyCount() {
        String value = System.getProperty("modl.trusted-proxy-count");
        if (value == null) {
            value = System.getProperty("MODL_TRUSTED_PROXY_COUNT");
        }
        if (value == null) {
            value = System.getenv("MODL_TRUSTED_PROXY_COUNT");
        }
        if (value == null || value.isBlank()) {
            return 1;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @NotNull
    public static Server getRequestServer(HttpServletRequest request) {
        return Objects.requireNonNull((Server) request.getAttribute(RequestAttribute.SERVER), "Server should not be null if being called from panel route!");
    }

    @Nullable
    public static String getSessionEmail(HttpServletRequest request) {
        AuthSessionData session = getSession(request);
        return session != null ? session.getEmail() : null;
    }

    @Nullable
    public static AuthSessionData getSession(HttpServletRequest request) {
        return (AuthSessionData) request.getAttribute(RequestAttribute.SESSION);
    }

    @Nullable
    public static String getActingStaffId(HttpServletRequest request) {
        String actingStaffId = request.getHeader("X-Acting-Staff-Id");
        if (actingStaffId == null) {
            return null;
        }
        String trimmed = actingStaffId.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        if (!TRUST_PROXY_HEADERS) {
            warnAboutUntrustedForwardingHeaderOnce(request);
            return request.getRemoteAddr();
        }

        String authoritative = request.getHeader(CLIENT_IP_HEADER);
        if (authoritative != null && isValidIp(authoritative.trim())) {
            return authoritative.trim();
        }

        String forwarded = firstValidIp(request.getHeader("X-Forwarded-For"), TRUSTED_PROXY_COUNT);
        if (forwarded != null) {
            return forwarded;
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && isValidIp(realIp.trim())) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private static void warnAboutUntrustedForwardingHeaderOnce(HttpServletRequest request) {
        if (warnedAboutProxy) {
            return;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor == null || xForwardedFor.isEmpty()) {
            return;
        }
        log.warn("Request has X-Forwarded-For header ({}) but MODL_TRUST_PROXY_HEADERS is not set. "
            + "Client IP will be reported as {}. Set MODL_TRUST_PROXY_HEADERS=true if running behind a proxy.",
            xForwardedFor, request.getRemoteAddr());
        warnedAboutProxy = true;
    }

    static String firstValidIp(String headerValue, int trustedProxyCount) {
        if (headerValue == null || headerValue.isEmpty()) {
            return null;
        }
        String[] entries = headerValue.split(",");
        int index = entries.length - trustedProxyCount;
        if (index < 0 || index >= entries.length) {
            return null;
        }
        String candidate = entries[index].trim();
        return isValidIp(candidate) ? candidate : null;
    }

    private static boolean isValidIp(String value) {
        if (value == null) {
            return false;
        }
        String candidate = value.trim();
        if (candidate.isEmpty()) {
            return false;
        }
        if (candidate.length() > 1 && candidate.charAt(0) == '[' && candidate.charAt(candidate.length() - 1) == ']') {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        if (candidate.indexOf(':') < 0) {
            return isIpv4Literal(candidate);
        }
        if (!IPV6_LITERAL_CHARS.matcher(candidate).matches()) {
            return false;
        }
        try {
            InetAddress.getByName(candidate);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isIpv4Literal(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3) {
                return false;
            }
            for (int i = 0; i < octet.length(); i++) {
                if (!Character.isDigit(octet.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    public static boolean trustsProxyHeaders() {
        return TRUST_PROXY_HEADERS;
    }

    public static String generateSecureToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
