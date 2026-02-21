package gg.modl.backend.auth.filter;

import gg.modl.backend.auth.AuthConfiguration;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final SessionService sessionService;
    private final AuthConfiguration authConfiguration;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        Set<String> sessionTokens = extractSessionTokens(request);
        log.debug("SessionAuthenticationFilter: path={}, sessionTokenCount={}", request.getRequestURI(), sessionTokens.size());

        if (!sessionTokens.isEmpty()) {
            Server server = (Server) request.getAttribute(RequestAttribute.SERVER);
            log.debug("SessionAuthenticationFilter: server attribute={}", server != null ? server.getCustomDomain() : "null");

            if (server != null) {
                for (String sessionToken : sessionTokens) {
                    if (authenticatePanelUser(request, server, sessionToken)) {
                        break;
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean authenticatePanelUser(HttpServletRequest request, Server server, String sessionToken) {
        Optional<AuthSessionData> sessionOpt = sessionService.findAndRefreshSession(server, sessionToken);
        log.debug("SessionAuthenticationFilter: session lookup result={}", sessionOpt.isPresent() ? "found" : "not found");

        if (sessionOpt.isEmpty()) {
            return false;
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
        return true;
    }

    private Set<String> extractSessionTokens(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Set.of();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> authConfiguration.getSessionCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
