package gg.modl.backend.rest.middleware;

import gg.modl.backend.rest.RequestAttribute;
import gg.modl.backend.rest.RequestHeader;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

@RequiredArgsConstructor
public class PanelFilter implements Filter {
    private final ServerService serverService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String serverDomain = httpRequest.getHeader(RequestHeader.SERVER_DOMAIN);
        if (serverDomain == null || serverDomain.isBlank()) {
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "");
            return;
        }

        System.out.println(serverDomain);
        Server server = serverService.getServerFromDomain(serverDomain);
        if (server == null) {
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "");
            return;
        }

        httpRequest.setAttribute(RequestAttribute.SERVER, server);
        chain.doFilter(request, response);
    }
}
