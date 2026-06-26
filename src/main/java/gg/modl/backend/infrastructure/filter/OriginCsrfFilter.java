package gg.modl.backend.infrastructure.filter;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.infrastructure.config.ModlCorsProperties;
import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RESTSecurityRole;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.infrastructure.rest.RequestHeader;
import gg.modl.backend.infrastructure.util.HostExtractionUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class OriginCsrfFilter extends OncePerRequestFilter {
    private final AuthConfiguration authConfiguration;
    private final ModlCorsProperties corsProperties;
    private final ModlProperties modlProperties;
    private volatile Set<String> parsedSystemOrigins = Set.of();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        final String method = request.getMethod();
        if (method.equalsIgnoreCase("GET") || method.equalsIgnoreCase("HEAD") || method.equalsIgnoreCase("OPTIONS")) {
            return true;
        }

        final String path = request.getRequestURI();
        final boolean panelOrAdmin = path.startsWith(RESTMappingV1.PREFIX_PANEL + "/")
                                     || path.equals(RESTMappingV1.PREFIX_PANEL)
                                     || path.startsWith(RESTMappingV1.PREFIX_ADMIN + "/")
                                     || path.equals(RESTMappingV1.PREFIX_ADMIN);

        return !panelOrAdmin || !hasSessionCookie(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain)
        throws ServletException, IOException {
        final String origin = request.getHeader("Origin");
        if (origin != null) {
            if (isAllowedOrigin(request, origin)) {
                filterChain.doFilter(request, response);
                return;
            }
            reject(response);
            return;
        }

        final String referer = request.getHeader("Referer");
        if (referer != null) {
            if (isAllowedReferer(request, referer)) {
                filterChain.doFilter(request, response);
                return;
            }
            reject(response);
            return;
        }

        // Accept only same-origin; cross-subdomain (same-site) is intentionally rejected so
        // cross-tenant CSRF isolation does not depend on the coarse registrable-domain signal.
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null && "same-origin".equalsIgnoreCase(fetchSite)) {
            filterChain.doFilter(request, response);
            return;
        }

        reject(response);
    }

    private boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }

        String panelSessionCookie = authConfiguration.getSessionCookieName();
        return Arrays.stream(cookies)
            .map(Cookie::getName)
            .anyMatch(name -> panelSessionCookie.equals(name) || RESTSecurityRole.ADMIN_SESSION_COOKIE.equals(name));
    }

    @PostConstruct
    void initParsedOrigins() {
        Set<String> origins = new HashSet<>(HostExtractionUtil.parseCommaSeparated(corsProperties.getSystemOrigins()));
        if (modlProperties.isDevelopmentMode()) {
            origins.add("http://localhost:3000");
            origins.add("http://localhost:5173");
        }
        parsedSystemOrigins = origins;
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"status\":403,\"error\":\"Cross-site request blocked\",\"message\":\"Cross-site request blocked\"}");
    }

    private boolean isAllowedOrigin(HttpServletRequest request, String origin) {
        // Panel writes are pinned to the tenant's own origin; the system-origin shortcut is
        // retained only for non-panel/admin paths, plus a dev-mode localhost allowance for the
        // panel dev server.
        if (isPanelPath(request.getRequestURI())) {
            String originHost = HostExtractionUtil.extractHost(origin);
            if (originHost == null) {
                return false;
            }
            String serverDomain = resolveRequestServerDomain(request);
            if (serverDomain != null && originHost.equalsIgnoreCase(serverDomain)) {
                return true;
            }
            return modlProperties.isDevelopmentMode() && parsedSystemOrigins.contains(origin);
        }

        return parsedSystemOrigins.contains(origin);
    }

    private boolean isAllowedReferer(HttpServletRequest request, String referer) {
        try {
            URI uri = URI.create(referer);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return false;
            }

            StringBuilder originBuilder = new StringBuilder()
                .append(scheme)
                .append("://")
                .append(host);
            if (uri.getPort() != -1) {
                originBuilder.append(":").append(uri.getPort());
            }

            return isAllowedOrigin(request, originBuilder.toString());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isPanelPath(String path) {
        return path != null && (path.startsWith(RESTMappingV1.PREFIX_PANEL + "/")
                                || path.equals(RESTMappingV1.PREFIX_PANEL));
    }

    private String resolveRequestServerDomain(HttpServletRequest request) {
        Object attributeValue = request.getAttribute(RequestAttribute.SERVER_DOMAIN);
        if (attributeValue instanceof String serverDomain && !serverDomain.isBlank()) {
            return serverDomain.trim();
        }

        String serverDomainHeader = request.getHeader(RequestHeader.SERVER_DOMAIN);
        return HostExtractionUtil.extractHost(serverDomainHeader);
    }

}
