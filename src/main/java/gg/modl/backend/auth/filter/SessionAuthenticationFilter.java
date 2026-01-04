package gg.modl.backend.auth.filter;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.auth.AuthService;
import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.rest.RESTSecurityRole;
import gg.modl.backend.rest.RequestAttribute;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final SessionService sessionService;
    private final AuthService authService;
    private final AuthConfiguration authConfiguration;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        String sessionToken = extractSessionToken(request);
        log.debug("SessionAuthenticationFilter: path={}, sessionToken={}", request.getRequestURI(), sessionToken != null ? "present" : "null");

        if (sessionToken != null) {
            Server server = (Server) request.getAttribute(RequestAttribute.SERVER);
            log.debug("SessionAuthenticationFilter: server attribute={}", server != null ? server.getCustomDomain() : "null");

            if (server != null) {
                authenticatePanelUser(request, server, sessionToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticatePanelUser(HttpServletRequest request, Server server, String sessionToken) {
        Optional<AuthSessionData> sessionOpt = sessionService.findAndRefreshSession(server, sessionToken);
        log.debug("SessionAuthenticationFilter: session lookup result={}", sessionOpt.isPresent() ? "found" : "not found");

        if (sessionOpt.isEmpty()) {
            return;
        }

        AuthSessionData session = sessionOpt.get();
        request.setAttribute(RequestAttribute.SESSION, session);

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(RESTSecurityRole.USER)
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        session.getEmail(),
                        null,
                        authorities
                );

        authentication.setDetails(session);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> authConfiguration.getSessionCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
