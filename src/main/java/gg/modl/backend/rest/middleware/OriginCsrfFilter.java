package gg.modl.backend.rest.middleware;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.rest.RESTMappingV1;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OriginCsrfFilter extends OncePerRequestFilter {
    private static final String ADMIN_SESSION_COOKIE = "modl.admin.session";

    private final AuthConfiguration authConfiguration;

    @Value("${modl.cors.system-origins:https://modl.gg,https://admin.modl.gg,https://modl.top,https://admin.modl.top}")
    private String systemOrigins;

    @Value("${modl.development-mode:false}")
    private boolean developmentMode;

    private volatile Set<String> parsedSystemOrigins = Set.of();

    @PostConstruct
    void initParsedOrigins() {
        if (systemOrigins != null && !systemOrigins.isBlank()) {
            parsedSystemOrigins = Arrays.stream(systemOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (developmentMode) {
            return true;
        }

        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String path = request.getRequestURI();
        boolean panelOrAdmin = path.startsWith(RESTMappingV1.PREFIX_PANEL + "/")
                || path.equals(RESTMappingV1.PREFIX_PANEL)
                || path.startsWith(RESTMappingV1.PREFIX_ADMIN + "/")
                || path.equals(RESTMappingV1.PREFIX_ADMIN);
        if (!panelOrAdmin) {
            return true;
        }

        return !hasSessionCookie(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && isAllowedOrigin(origin)) {
            filterChain.doFilter(request, response);
            return;
        }

        String referer = request.getHeader("Referer");
        if (referer != null && isAllowedReferer(referer)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"Cross-site request blocked\"}");
    }

    private boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }

        String panelSessionCookie = authConfiguration.getSessionCookieName();
        return Arrays.stream(cookies)
                .map(Cookie::getName)
                .anyMatch(name -> panelSessionCookie.equals(name) || ADMIN_SESSION_COOKIE.equals(name));
    }

    private boolean isAllowedOrigin(String origin) {
        return parsedSystemOrigins.contains(origin);
    }

    private boolean isAllowedReferer(String referer) {
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return false;
            }

            StringBuilder originBuilder = new StringBuilder()
                    .append(uri.getScheme())
                    .append("://")
                    .append(uri.getHost());
            if (uri.getPort() != -1) {
                originBuilder.append(":").append(uri.getPort());
            }

            return isAllowedOrigin(originBuilder.toString());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

}
