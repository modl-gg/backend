package gg.modl.backend.infrastructure.filter;

import gg.modl.backend.admin.service.AdminAuthService;
import gg.modl.backend.infrastructure.rest.RESTSecurityRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {
    private final AdminAuthService adminAuthService;
    public static final String ADMIN_SESSION_ATTR = "adminSession";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/v1/admin/") && !path.startsWith("/v1/admin/auth/")) {
            Optional<AdminAuthService.AdminSession> sessionOpt = adminAuthService.getAuthenticatedSession(request);
            if (sessionOpt.isPresent()) {
                AdminAuthService.AdminSession session = sessionOpt.get();
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
