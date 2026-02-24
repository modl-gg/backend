package gg.modl.backend.rest.middleware;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestAttribute;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PanelPermissionFilter extends OncePerRequestFilter {
    private final PermissionService permissionService;
    private final StaffService staffService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith(RESTMappingV1.PREFIX_PANEL)) {
            return true;
        }
        return startsWithEndpoint(path, RESTMappingV1.PANEL_AUTH);
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

        if (permissionService.isSuperAdmin(server, email)) {
            filterChain.doFilter(request, response);
            return;
        }

        String requiredPermission = resolveRequiredPermission(request.getRequestURI(), request.getMethod());
        if (requiredPermission == null) {
            deny(response);
            return;
        }

        Optional<Staff> staffOpt = staffService.getStaffByEmail(server, email);
        String role = staffOpt.map(Staff::getRole).orElse(null);
        boolean authorized = role != null && permissionService.hasPermission(server, role, requiredPermission);
        if (!authorized) {
            deny(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void deny(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"Insufficient permissions\"}");
    }

    private String resolveRequiredPermission(String path, String method) {
        if (startsWithEndpoint(path, RESTMappingV1.PANEL_STAFF)) {
            return "admin.staff.manage.members";
        }
        if (startsWithEndpoint(path, RESTMappingV1.PANEL_ROLES)) {
            return "admin.staff.manage.roles";
        }

        if (startsWithEndpoint(path, RESTMappingV1.PANEL_BILLING)) {
            return isReadOnly(method) ? "admin.settings.view.billing" : "admin.settings.modify.billing";
        }
        if (startsWithEndpoint(path, RESTMappingV1.PANEL_HOMEPAGE_CARDS)
                || startsWithEndpoint(path, RESTMappingV1.PANEL_KNOWLEDGEBASE)
                || startsWithEndpoint(path, RESTMappingV1.PANEL_MEDIA)) {
            return isReadOnly(method) ? "admin.settings.view.content" : "admin.settings.modify.content";
        }
        if (startsWithEndpoint(path, RESTMappingV1.PANEL_DOMAIN)) {
            return isReadOnly(method) ? "admin.settings.view.domain" : "admin.settings.modify.domain";
        }
        if (startsWithEndpoint(path, RESTMappingV1.PANEL_MIGRATION)) {
            return isReadOnly(method) ? "admin.settings.view.migration" : "admin.settings.modify.migration";
        }
        if (startsWithEndpoint(path, RESTMappingV1.PANEL_STORAGE)) {
            return isReadOnly(method) ? "admin.settings.view.storage" : "admin.settings.modify.storage";
        }
        if (startsWithEndpoint(path, RESTMappingV1.PANEL_SETTINGS)) {
            return resolveSettingsPermission(path, method);
        }

        if (startsWithEndpoint(path, RESTMappingV1.PANEL_DASHBOARD)) {
            return "admin.audit.view.dashboard";
        }
        if (startsWithEndpoint(path, RESTMappingV1.PANEL_ANALYTICS)) {
            return "admin.audit.view.analytics";
        }
        if (startsWithEndpoint(path, RESTMappingV1.PANEL_AUDIT)
                || startsWithEndpoint(path, RESTMappingV1.PANEL_LOGS)) {
            return "admin.audit.view.logs";
        }

        if (startsWithEndpoint(path, RESTMappingV1.PANEL_TICKET_SUBSCRIPTIONS)) {
            return isReadOnly(method) ? "ticket.view.all" : "ticket.reply.all";
        }
        if (startsWithEndpoint(path, RESTMappingV1.PANEL_TICKETS)) {
            return isReadOnly(method) ? "ticket.view.all" : "ticket.reply.all";
        }

        if (startsWithEndpoint(path, RESTMappingV1.PANEL_PLAYERS)) {
            return "punishment.modify";
        }

        if (startsWithEndpoint(path, RESTMappingV1.PANEL_APPEALS)) {
            return isReadOnly(method) ? "ticket.view.all" : "ticket.reply.all";
        }

        if (startsWithEndpoint(path, RESTMappingV1.PANEL_SERVER)) {
            return isReadOnly(method) ? "admin.settings.view" : "admin.settings.modify";
        }

        return null;
    }

    private String resolveSettingsPermission(String path, String method) {
        String base = RESTMappingV1.PANEL_SETTINGS;

        if (startsWithEndpoint(path, base + "/punishment-types")
                || startsWithEndpoint(path, base + "/status-thresholds")
                || startsWithEndpoint(path, base + "/ai-moderation")
                || startsWithEndpoint(path, base + "/ai-apply-punishment")
                || startsWithEndpoint(path, base + "/ai-dismiss-suggestion")) {
            return isReadOnly(method) ? "admin.settings.view.punishments" : "admin.settings.modify.punishments";
        }

        if (startsWithEndpoint(path, base + "/domain")) {
            return isReadOnly(method) ? "admin.settings.view.domain" : "admin.settings.modify.domain";
        }

        if (startsWithEndpoint(path, base + "/general")
                || startsWithEndpoint(path, base + "/upload-icon")
                || startsWithEndpoint(path, base + "/api-keys")
                || startsWithEndpoint(path, base + "/quick-responses")
                || startsWithEndpoint(path, base + "/ticket-forms")
                || startsWithEndpoint(path, base + "/ticket-labels")
                || startsWithEndpoint(path, base + "/webhooks")) {
            return isReadOnly(method) ? "admin.settings.view" : "admin.settings.modify";
        }

        return isReadOnly(method) ? "admin.settings.view" : "admin.settings.modify";
    }

    private boolean startsWithEndpoint(String path, String endpoint) {
        return path.equals(endpoint) || path.startsWith(endpoint + "/");
    }

    private boolean isReadOnly(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
