package gg.modl.backend.rest.middleware;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestAttribute;
import gg.modl.backend.rest.RequestHeader;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class ServerHeaderFilter extends OncePerRequestFilter {
    private final ServerService serverService;
    private final boolean developmentMode;
    private final String devServerDomain;
    private final Set<String> systemOrigins;

    private static final Set<String> EXCLUDED_PATHS = Set.of(
            RESTMappingV1.PUBLIC_REGISTRATION,
            RESTMappingV1.PUBLIC_EVIDENCE_UPLOAD
    );

    public ServerHeaderFilter(ServerService serverService) {
        this(serverService, false, null, null);
    }

    public ServerHeaderFilter(
            ServerService serverService,
            boolean developmentMode,
            @Nullable String devServerDomain,
            @Nullable String systemOrigins
    ) {
        this.serverService = serverService;
        this.developmentMode = developmentMode;
        this.devServerDomain = devServerDomain;
        this.systemOrigins = parseSystemOrigins(systemOrigins);
    }

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain) throws ServletException, IOException {
        String serverDomain = request.getHeader(RequestHeader.SERVER_DOMAIN);

        // In development mode, use the configured dev server domain if header is localhost
        if (developmentMode && devServerDomain != null && !devServerDomain.isBlank()) {
            if (serverDomain == null || serverDomain.isBlank() ||
                serverDomain.equals("localhost") || serverDomain.startsWith("127.0.0.1")) {
                serverDomain = devServerDomain;
            }
        }

        if (serverDomain == null || serverDomain.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing server domain (X-Server-Domain)");
            return;
        }

        String normalizedServerDomain = normalizeServerDomain(serverDomain);
        if (normalizedServerDomain == null || normalizedServerDomain.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid server domain format");
            return;
        }

        Server server = serverService.getServerFromDomain(normalizedServerDomain);
        if (server == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid server domain: " + normalizedServerDomain);
            return;
        }

        if (!isOriginAllowedForServer(request, normalizedServerDomain)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin is not allowed for requested server domain");
            return;
        }

        request.setAttribute(RequestAttribute.SERVER, server);
        chain.doFilter(request, response);
    }

    private Set<String> parseSystemOrigins(@Nullable String origins) {
        if (origins == null || origins.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean isOriginAllowedForServer(HttpServletRequest request, String serverDomain) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return true;
        }

        if (systemOrigins.contains(origin)) {
            return true;
        }

        String originHost = extractHost(origin);
        if (originHost == null) {
            return false;
        }

        if (developmentMode && isLocalhost(originHost)) {
            return true;
        }

        String normalizedDomain = extractHost(serverDomain);
        return normalizedDomain != null && originHost.equalsIgnoreCase(normalizedDomain);
    }

    private boolean isLocalhost(String host) {
        String normalized = host.toLowerCase();
        return "localhost".equals(normalized) || "0.0.0.0".equals(normalized)
                || "::1".equals(normalized) || normalized.startsWith("127.");
    }

    private String extractHost(String originOrDomain) {
        if (originOrDomain == null || originOrDomain.isBlank()) {
            return null;
        }

        String value = originOrDomain.trim();
        String normalized = value.contains("://") ? value : "https://" + value;

        try {
            return URI.create(normalized).getHost();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeServerDomain(String serverDomain) {
        String host = extractHost(serverDomain);
        if (host == null || host.isBlank()) {
            return null;
        }
        return host;
    }
}
