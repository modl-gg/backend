package gg.modl.backend.infrastructure.util;

import gg.modl.backend.auth.AuthConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final AuthConfiguration authConfiguration;

    public Cookie createSessionCookie(String sessionId) {
        return createSessionCookie(
            authConfiguration.getSessionCookieName(),
            sessionId,
            authConfiguration.getSessionDurationSeconds()
        );
    }

    public Cookie createSessionCookie(String cookieName, String sessionId, long maxAgeSeconds) {
        Cookie cookie = baseCookie(cookieName, sessionId);
        cookie.setMaxAge((int) maxAgeSeconds);
        return cookie;
    }

    public List<Cookie> createExpiredSessionCookies() {
        return createExpiredSessionCookies(authConfiguration.getSessionCookieName());
    }

    public List<Cookie> createExpiredSessionCookies(String cookieName) {
        List<Cookie> cookies = new ArrayList<>();
        cookies.add(createExpiredCookie(cookieName, null));

        String domain = getConfiguredCookieDomain();
        if (domain != null) {
            cookies.add(createExpiredCookie(cookieName, domain));
            if (!domain.startsWith(".")) {
                cookies.add(createExpiredCookie(cookieName, "." + domain));
            } else if (domain.length() > 1) {
                cookies.add(createExpiredCookie(cookieName, domain.substring(1)));
            }
        }

        return cookies;
    }

    private Cookie createExpiredCookie(String cookieName, String domain) {
        Cookie cookie = baseCookie(cookieName, "");
        cookie.setMaxAge(0);
        if (domain != null) {
            cookie.setDomain(domain);
        }
        return cookie;
    }

    private Cookie baseCookie(String name, String value) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(authConfiguration.isCookieSecure());
        cookie.setPath("/");
        cookie.setAttribute("SameSite", authConfiguration.isDevelopmentMode() ? "Lax" : "Strict");
        return cookie;
    }

    private String getConfiguredCookieDomain() {
        String domain = authConfiguration.getCookieDomain();
        return (domain == null || domain.isBlank()) ? null : domain;
    }
}
