package gg.modl.backend.staff.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.response.MinecraftStaffSummaryResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MinecraftStaffServiceSummaryTest {

    private final StaffMongoRepository staffRepository = mock(StaffMongoRepository.class);
    private final PlayerMongoRepository playerRepository = mock(PlayerMongoRepository.class);
    private final PunishmentMongoRepository punishmentRepository = mock(PunishmentMongoRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final MinecraftStaffService service = new MinecraftStaffService(
        staffRepository,
        playerRepository,
        punishmentRepository,
        mock(PlayerService.class),
        permissionService,
        mock(RoleAuthorization.class),
        mock(ServerTimestampService.class),
        mock(StaffLookupCache.class)
    );

    @Test
    void summaryHandlesStaffWithoutLinkedMinecraftAccount() {
        Server server = server();
        Staff unlinked = Staff.builder()
            .id("unlinked-id")
            .email("unlinked@example.com")
            .username("Unlinked")
            .roleId("admin")
            .build();
        when(staffRepository.findAll(server)).thenReturn(List.of(unlinked));
        when(punishmentRepository.countPunishmentsByEffectiveIssuer(any(Server.class))).thenReturn(Map.of());
        when(permissionService.getRolesByIds(any(Server.class), any())).thenReturn(Map.of());

        List<MinecraftStaffSummaryResponse> summary =
            assertDoesNotThrow(() -> service.getMinecraftStaffSummary(server));

        assertEquals(1, summary.size());
        MinecraftStaffSummaryResponse row = summary.get(0);
        assertNull(row.minecraftUuid());
        assertEquals(0L, row.totalPlaytimeMs());
        assertNull(row.lastServer());
        verify(playerRepository, never()).findByMinecraftUuids(any(Server.class), any(Collection.class));
    }

    @Test
    void summaryReportsPlaytimeAndLastServerForLinkedStaff() {
        Server server = server();
        String uuid = "11111111-1111-1111-1111-111111111111";
        Staff linked = Staff.builder()
            .id("linked-id")
            .email("linked@example.com")
            .username("Linked")
            .roleId("admin")
            .assignedMinecraftUuid(uuid)
            .assignedMinecraftUsername("LinkedMc")
            .build();
        Player player = Player.builder()
            .minecraftUuid(UUID.fromString(uuid))
            .data(Map.of("totalPlaytimeSeconds", 120, "lastServer", "lobby"))
            .build();
        when(staffRepository.findAll(server)).thenReturn(List.of(linked));
        when(playerRepository.findByMinecraftUuids(eq(server), eq(List.of(uuid)))).thenReturn(List.of(player));
        when(punishmentRepository.countPunishmentsByEffectiveIssuer(any(Server.class))).thenReturn(Map.of());
        when(permissionService.getRolesByIds(any(Server.class), any())).thenReturn(Map.of());

        List<MinecraftStaffSummaryResponse> summary = service.getMinecraftStaffSummary(server);

        assertEquals(1, summary.size());
        MinecraftStaffSummaryResponse row = summary.get(0);
        assertEquals(uuid, row.minecraftUuid());
        assertEquals(120000L, row.totalPlaytimeMs());
        assertEquals("lobby", row.lastServer());
    }

    private Server server() {
        Server server = new Server("Server", "server", "server_db", "owner@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
        return server;
    }
}
