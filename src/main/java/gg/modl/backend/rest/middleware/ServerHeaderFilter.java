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
import java.util.Set;

public class ServerHeaderFilter extends OncePerRequestFilter {
    private final ServerService serverService;
    private final boolean developmentMode;
    private final String devServerDomain;

    private static final Set<String> EXCLUDED_PATHS = Set.of(
            RESTMappingV1.PUBLIC_REGISTRATION
    );

    public ServerHeaderFilter(ServerService serverService) {
        this(serverService, false, null);
    }

    public ServerHeaderFilter(ServerService serverService, boolean developmentMode, @Nullable String devServerDomain) {
        this.serverService = serverService;
        this.developmentMode = developmentMode;
        this.devServerDomain = devServerDomain;
    }

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
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
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "");
            return;
        }

        Server server = serverService.getServerFromDomain(serverDomain);
        if (server == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "");
            return;
        }

        request.setAttribute(RequestAttribute.SERVER, server);
        chain.doFilter(request, response);
    }
}
