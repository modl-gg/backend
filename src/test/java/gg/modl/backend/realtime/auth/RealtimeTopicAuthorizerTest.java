package gg.modl.backend.realtime.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffService;
import gg.modl.proto.modl.v1.Topic;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RealtimeTopicAuthorizerTest {

    @Test
    void panelTicketTopicsRequireReadPermission() {
        PermissionService permissionService = mock(PermissionService.class);
        StaffService staffService = mock(StaffService.class);
        RealtimeTopicAuthorizer authorizer = new RealtimeTopicAuthorizer(permissionService, staffService);
        Server server = server();

        Staff staff = Staff.builder()
            .email("staff@example.com")
            .roleId("support")
            .build();
        when(staffService.getStaffByEmail(server, "staff@example.com")).thenReturn(Optional.of(staff));
        when(permissionService.hasPermission(server, "support", "ticket.view.all")).thenReturn(true);

        assertTrue(authorizer.canSubscribe(
            RealtimePrincipal.panel(server, "staff@example.com"),
            Topic.TOPIC_PANEL_TICKETS
        ));
    }

    @Test
    void panelPermissionScopesAreEnforcedPerTopic() {
        PermissionService permissionService = mock(PermissionService.class);
        StaffService staffService = mock(StaffService.class);
        RealtimeTopicAuthorizer authorizer = new RealtimeTopicAuthorizer(permissionService, staffService);
        Server server = server();

        Staff staff = Staff.builder()
            .email("staff@example.com")
            .roleId("support")
            .build();
        when(staffService.getStaffByEmail(server, "staff@example.com")).thenReturn(Optional.of(staff));
        when(permissionService.hasPermission(server, "support", "ticket.view.all")).thenReturn(true);

        RealtimePrincipal principal = RealtimePrincipal.panel(server, "staff@example.com");
        assertTrue(authorizer.canSubscribe(principal, Topic.TOPIC_PANEL_NOTIFICATIONS));
        assertFalse(authorizer.canSubscribe(principal, Topic.TOPIC_PANEL_STAFF));
    }

    @Test
    void panelCannotSubscribeToMinecraftTopics() {
        RealtimeTopicAuthorizer authorizer = new RealtimeTopicAuthorizer(mock(PermissionService.class), mock(StaffService.class));

        assertFalse(authorizer.canSubscribe(
            RealtimePrincipal.panel(server(), "staff@example.com"),
            Topic.TOPIC_MINECRAFT_PRESENCE
        ));
    }

    @Test
    void minecraftCanSubscribeToAllMinecraftTopics() {
        RealtimeTopicAuthorizer authorizer = new RealtimeTopicAuthorizer(mock(PermissionService.class), mock(StaffService.class));
        RealtimePrincipal principal = RealtimePrincipal.minecraft(server(), "instance-1");

        assertTrue(authorizer.canSubscribe(principal, Topic.TOPIC_MINECRAFT_PERMISSIONS));
        assertTrue(authorizer.canSubscribe(principal, Topic.TOPIC_MINECRAFT_PUNISHMENT_TYPES));
        assertTrue(authorizer.canSubscribe(principal, Topic.TOPIC_MINECRAFT_STAFF_NOTIFICATIONS));
        assertTrue(authorizer.canSubscribe(principal, Topic.TOPIC_MINECRAFT_PRESENCE));
        assertTrue(authorizer.canSubscribe(principal, Topic.TOPIC_MINECRAFT_PUNISHMENTS));
        assertTrue(authorizer.canSubscribe(principal, Topic.TOPIC_MINECRAFT_PLAYER_NOTIFICATIONS));
        assertTrue(authorizer.canSubscribe(principal, Topic.TOPIC_MINECRAFT_STAFF_2FA));
        assertTrue(authorizer.canSubscribe(principal, Topic.TOPIC_MINECRAFT_MIGRATION_TASKS));
        assertTrue(authorizer.canSubscribe(principal, Topic.TOPIC_MINECRAFT_STAT_WIPES));
        assertFalse(authorizer.canSubscribe(principal, Topic.TOPIC_PANEL_TICKETS));
    }

    private Server server() {
        Server server = new Server("server", "server", "server_db", "admin@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
        return server;
    }
}
