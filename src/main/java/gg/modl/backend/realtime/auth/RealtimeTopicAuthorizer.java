package gg.modl.backend.realtime.auth;

import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffLookupCache;
import gg.modl.proto.modl.v1.Topic;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeTopicAuthorizer {
    private final PermissionService permissionService;
    private final StaffLookupCache staffLookupCache;

    public boolean canSubscribe(RealtimePrincipal principal, Topic topic) {
        if (topic == null || topic == Topic.TOPIC_UNSPECIFIED) {
            return false;
        }

        if (principal.clientKind() == RealtimeClientKind.MINECRAFT) {
            return isMinecraftTopic(principal, topic);
        }

        if (principal.clientKind() == RealtimeClientKind.PANEL) {
            return switch (topic) {
                case TOPIC_PANEL_TICKETS, TOPIC_PANEL_ASSIGNED_TICKETS,
                     TOPIC_PANEL_NOTIFICATIONS, TOPIC_PANEL_APPEALS
                    -> hasPanelPermission(principal, "ticket.view.all");
                case TOPIC_PANEL_MIGRATIONS -> hasPanelPermission(principal, "admin.settings.view.migration");
                case TOPIC_PANEL_PLAYERS, TOPIC_PANEL_PUNISHMENTS -> hasPanelPermission(principal, "punishment.modify");
                case TOPIC_PANEL_STAFF -> hasPanelPermission(principal, "admin.staff.manage.members");
                case TOPIC_PANEL_ROLES -> hasPanelPermission(principal, "admin.staff.manage.roles");
                case TOPIC_PANEL_PUNISHMENT_TYPES -> hasPanelPermission(principal, "admin.settings.view.punishments");
                case TOPIC_PANEL_KNOWLEDGEBASE, TOPIC_PANEL_HOMEPAGE
                    -> hasPanelPermission(principal, "admin.settings.view.content");
                case TOPIC_PANEL_SETTINGS -> hasPanelPermission(principal, "admin.settings.view");
                case TOPIC_PANEL_AUDIT -> hasPanelPermission(principal, "admin.audit.view.logs");
                case TOPIC_PANEL_DASHBOARD -> hasPanelPermission(principal, "admin.audit.view.dashboard");
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

        Optional<Staff> staffOpt = staffLookupCache.findByEmail(server, email);
        String roleId = staffOpt.map(Staff::getRoleId).orElse(null);
        return roleId != null && permissionService.hasPermission(server, roleId, permission);
    }

    private boolean isMinecraftTopic(RealtimePrincipal principal, Topic topic) {
        return switch (topic) {
            case TOPIC_MINECRAFT_PERMISSIONS,
                 TOPIC_MINECRAFT_PUNISHMENT_TYPES,
                 TOPIC_MINECRAFT_STAFF_NOTIFICATIONS,
                 TOPIC_MINECRAFT_PRESENCE,
                 TOPIC_MINECRAFT_PUNISHMENTS,
                 TOPIC_MINECRAFT_PLAYER_NOTIFICATIONS,
                 TOPIC_MINECRAFT_STAFF_2FA,
                 TOPIC_MINECRAFT_MIGRATION_TASKS,
                 TOPIC_MINECRAFT_STAT_WIPES -> true;
            default -> false;
        };
    }
}
