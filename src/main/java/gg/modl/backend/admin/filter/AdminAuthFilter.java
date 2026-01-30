package gg.modl.backend.admin.filter;

import gg.modl.backend.admin.controller.AdminAuthController;
import gg.modl.backend.rest.RESTSecurityRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {
    public static final String ADMIN_SESSION_ATTR = "adminSession";

    private final AdminAuthController adminAuthController;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only process /v1/admin/* paths (excluding auth endpoints)
        if (path.startsWith("/v1/admin/") && !path.startsWith("/v1/admin/auth/")) {
            Optional<AdminAuthController.AdminSession> sessionOpt = adminAuthController.getAuthenticatedSession(request);
            if (sessionOpt.isPresent()) {
                AdminAuthController.AdminSession session = sessionOpt.get();
                request.setAttribute(ADMIN_SESSION_ATTR, session);

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority(RESTSecurityRole.ADMIN)
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                session.email(),
                                null,
                                authorities
                        );
                authentication.setDetails(session);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
