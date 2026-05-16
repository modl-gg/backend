package gg.modl.backend.infrastructure.filter;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RESTMappingV2;
import gg.modl.backend.infrastructure.rest.RESTMappingV3;
import gg.modl.backend.infrastructure.rest.RESTSecurityRole;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.infrastructure.rest.RequestHeader;
import gg.modl.backend.infrastructure.proto.ProtobufErrorResponseWriter;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.ApiKeySettingsService;
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
    private final ApiKeySettingsService apiKeyService;
    private final ProtobufErrorResponseWriter protobufErrorResponseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        final String path = request.getRequestURI();

        return !path.startsWith(RESTMappingV1.PREFIX_MINECRAFT)
            && !path.startsWith(RESTMappingV1.PREFIX_REPLAY_LITE)
            && !path.startsWith(RESTMappingV2.PREFIX_MINECRAFT)
            && !path.startsWith(RESTMappingV3.PREFIX_MINECRAFT);
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

        final Server server = apiKeyService.findServerByApiKey(apiKey);
        if (server == null) {
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
