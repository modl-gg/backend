package gg.modl.backend.infrastructure.filter;

import gg.modl.backend.infrastructure.authorization.PanelAccessPolicy;
import gg.modl.backend.infrastructure.authorization.PanelAccessPolicyResolver;
import gg.modl.backend.infrastructure.authorization.PanelAccessRequest;
import gg.modl.backend.infrastructure.authorization.PanelPrincipalPermissions;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.infrastructure.rest.RouteGroups;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class PanelPermissionFilter extends OncePerRequestFilter {
    private final PermissionService permissionService;
    private final RoleAuthorization roleAuthorization;
    private final PanelAccessPolicyResolver policyResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!RouteGroups.isPanelPrefix(path)) {
            return true;
        }
        return RouteGroups.isPanelAuthArea(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        @NotNull HttpServletResponse response,
        @NotNull FilterChain filterChain
    ) throws ServletException, IOException {
        Server server = (Server) request.getAttribute(RequestAttribute.SERVER);
        String email = RequestUtil.getSessionEmail(request);
        if (server == null || email == null) {
            deny(response);
            return;
        }

        boolean superAdmin = permissionService.isSuperAdmin(server, email);
        if (superAdmin) {
            filterChain.doFilter(request, response);
            return;
        }

        List<PanelAccessPolicy> policies = policyResolver.resolvePolicies(request);
        if (policies.isEmpty()) {
            deny(response);
            return;
        }

        PanelAccessRequest accessRequest = new PanelAccessRequest(request.getMethod(), request.getRequestURI());
        if (policies.stream().anyMatch(policy -> policy.permitsWithoutRole(accessRequest))) {
            filterChain.doFilter(request, response);
            return;
        }

        String roleId = roleAuthorization.panelRoleId(server, email);
        PanelPrincipalPermissions permissions = new PanelPrincipalPermissions(server, roleId, superAdmin, permissionService);

        if (policies.stream().anyMatch(policy -> policy.permitsWithRole(accessRequest, permissions))) {
            filterChain.doFilter(request, response);
            return;
        }

        deny(response);
    }

    private void deny(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"status\":403,\"error\":\"Insufficient permissions\",\"message\":\"Insufficient permissions\"}");
    }
}
