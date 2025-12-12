package gg.modl.backend.rest.middleware;

import gg.modl.backend.rest.RequestAttribute;
import gg.modl.backend.rest.RequestHeader;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ServerHeaderFilter extends OncePerRequestFilter {
    private final ServerService serverService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain) throws ServletException, IOException {
        String serverDomain = request.getHeader(RequestHeader.SERVER_DOMAIN);
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
