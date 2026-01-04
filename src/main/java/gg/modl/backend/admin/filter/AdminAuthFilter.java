package gg.modl.backend.admin.filter;

import gg.modl.backend.admin.controller.AdminAuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(2)
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {
    public static final String ADMIN_SESSION_ATTR = "adminSession";

    private final AdminAuthController adminAuthController;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only filter /v1/admin/* paths
        if (!path.startsWith("/v1/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Allow auth endpoints without authentication
        if (path.startsWith("/v1/admin/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check authentication for all other admin endpoints
        var sessionOpt = adminAuthController.getAuthenticatedSession(request);
        if (sessionOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"Not authenticated\"}");
            return;
        }

        // Store session in request for use by controllers
        request.setAttribute(ADMIN_SESSION_ATTR, sessionOpt.get());
        filterChain.doFilter(request, response);
    }
}
