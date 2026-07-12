package gg.modl.backend.infrastructure.filter;

import gg.modl.backend.infrastructure.config.StagingEnvironment;
import gg.modl.backend.infrastructure.rest.RESTSecurityRole;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.infrastructure.rest.RequestHeader;
import gg.modl.backend.infrastructure.rest.RouteGroups;
import gg.modl.backend.infrastructure.proto.ProtobufErrorResponseWriter;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {
    private final ServerService serverService;
    private final ProtobufErrorResponseWriter protobufErrorResponseWriter;
    private final StagingEnvironment stagingEnvironment;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        final String path = request.getRequestURI();

        return !RouteGroups.isMinecraftOrReplayLitePrefix(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        @NotNull HttpServletResponse response,
        @NotNull FilterChain chain
    ) throws ServletException, IOException {
        final String apiKey = request.getHeader(RequestHeader.API_KEY);

        if (apiKey == null || apiKey.isBlank()) {
            writeUnauthorized(request, response);
            return;
        }

        final Server server = serverService.getServerByApiKey(apiKey);
        if (server == null) {
            writeUnauthorized(request, response);
            return;
        }

        if (stagingEnvironment.isStaging() && !Boolean.TRUE.equals(server.getBetaTester())) {
            writeUnauthorized(request, response);
            return;
        }

        request.setAttribute(RequestAttribute.SERVER, server);

        final List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(RESTSecurityRole.MINECRAFT));
        final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(server.getId(), null, authorities);

        authentication.setDetails(server);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            protobufErrorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Unauthorized");
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
