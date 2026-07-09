package gg.modl.backend.infrastructure.filter;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class StagingBetaAccessFilter extends OncePerRequestFilter {
    private static final String RESTRICTED_BODY =
        "{\"success\":false,\"error\":\"This panel is restricted to verified beta testers.\"}";

    private static final Set<String> SELF_AUTHENTICATED_PUBLIC_PATHS = Set.of(
        RESTMappingV1.PUBLIC_EVIDENCE_UPLOAD
    );

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return SELF_AUTHENTICATED_PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain)
        throws ServletException, IOException {
        Object serverAttribute = request.getAttribute(RequestAttribute.SERVER);
        if (serverAttribute instanceof Server server && Boolean.TRUE.equals(server.getBetaTester())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(RESTRICTED_BODY);
    }
}
