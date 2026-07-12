package gg.modl.backend.staff.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.request.AssignMinecraftPlayerRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MinecraftStaffServiceUuidNormalizationTest {

    @Test
    void markStaffDisconnectedLowercasesUuidBeforeQueryingRepository() {
        StaffMongoRepository staffRepository = mock(StaffMongoRepository.class);
        MinecraftStaffService service = buildService(staffRepository);

        Server server = server();
        service.markStaffDisconnected(server, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE");

        verify(staffRepository).updateLastSeenByAssignedMinecraftUuid(server, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    }

    @Test
    void assignMinecraftPlayerLowercasesUuidBeforeQueryingPlayerRepository() {
        StaffMongoRepository staffRepository = mock(StaffMongoRepository.class);
        PlayerMongoRepository playerRepository = mock(PlayerMongoRepository.class);
        MinecraftStaffService service = buildService(staffRepository, playerRepository);

        Server server = server();
        Staff staff = Staff.builder()
            .id("staff-id")
            .email("target@example.com")
            .username("target")
            .roleId("helper")
            .build();
        Player player = Player.builder()
            .minecraftUuid(UUID.randomUUID())
            .usernames(new ArrayList<>(List.of(new UsernameEntry("PlayerOne", new Date()))))
            .build();

        when(staffRepository.findByEmailIgnoreCase(server, "target@example.com")).thenReturn(Optional.of(staff));
        when(playerRepository.findByMinecraftUuid(eq(server), eq("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")))
            .thenReturn(Optional.of(player));
        when(staffRepository.findByAssignedMinecraftUuidExcludingId(any(), any(), any())).thenReturn(Optional.empty());
        when(staffRepository.saveEntity(any(Server.class), any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(1));

        service.assignMinecraftPlayer(server, "target@example.com",
            new AssignMinecraftPlayerRequest("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", null),
            new RoleAuthorization.PerformerAuthority("owner@example.com", null, true, true));

        verify(playerRepository).findByMinecraftUuid(server, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    }

    private MinecraftStaffService buildService(StaffMongoRepository staffRepository) {
        return buildService(staffRepository, mock(PlayerMongoRepository.class));
    }

    private MinecraftStaffService buildService(StaffMongoRepository staffRepository, PlayerMongoRepository playerRepository) {
        return new MinecraftStaffService(
            staffRepository,
            playerRepository,
            mock(PunishmentMongoRepository.class),
            mock(PlayerService.class),
            mock(PermissionService.class),
            mock(RoleAuthorization.class),
            mock(ServerTimestampService.class),
            mock(StaffLookupCache.class)
        );
    }

    private Server server() {
        Server server = new Server("Server", "server", "server_db", "owner@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
        return server;
    }
}
