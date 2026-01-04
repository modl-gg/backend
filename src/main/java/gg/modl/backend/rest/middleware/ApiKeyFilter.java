package gg.modl.backend.rest.middleware;

import gg.modl.backend.rest.RequestAttribute;
import gg.modl.backend.rest.RequestHeader;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {
    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain chain
    ) throws ServletException, IOException {
        String apiKey = request.getHeader(RequestHeader.API_KEY);

        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"API key required\"}");
            return;
        }

        Server server = apiKeyService.findServerByApiKey(apiKey);
        if (server == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid API key\"}");
            return;
        }

        request.setAttribute(RequestAttribute.SERVER, server);
        chain.doFilter(request, response);
    }
}
