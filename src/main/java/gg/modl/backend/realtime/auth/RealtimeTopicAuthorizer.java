package gg.modl.backend.realtime.auth;

import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffService;
import gg.modl.proto.modl.v1.Topic;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeTopicAuthorizer {
    private final PermissionService permissionService;
    private final StaffService staffService;

    public boolean canSubscribe(RealtimePrincipal principal, Topic topic) {
        if (topic == null || topic == Topic.TOPIC_UNSPECIFIED) {
            return false;
        }

        if (principal.clientKind() == RealtimeClientKind.MINECRAFT) {
            return isMinecraftTopic(principal, topic);
        }

        if (principal.clientKind() == RealtimeClientKind.PANEL) {
            return switch (topic) {
                case TOPIC_PANEL_TICKETS, TOPIC_PANEL_ASSIGNED_TICKETS -> hasPanelPermission(principal, "ticket.view.all");
                case TOPIC_PANEL_MIGRATIONS -> hasPanelPermission(principal, "admin.settings.view.migration");
                default -> false;
            };
        }

        return false;
    }

    private boolean hasPanelPermission(RealtimePrincipal principal, String permission) {
        Server server = principal.server();
        String email = principal.email();
        if (email == null || email.isBlank()) {
            return false;
        }

        if (permissionService.isSuperAdmin(server, email)) {
            return true;
        }

        Optional<Staff> staffOpt = staffService.getStaffByEmail(server, email);
        String roleId = staffOpt.map(Staff::getRoleId).orElse(null);
        return roleId != null && permissionService.hasPermission(server, roleId, permission);
    }

    private boolean isMinecraftTopic(RealtimePrincipal principal, Topic topic) {
        return switch (topic) {
            case TOPIC_MINECRAFT_PERMISSIONS,
                 TOPIC_MINECRAFT_PUNISHMENT_TYPES -> true;
            default -> false;
        };
    }
}
