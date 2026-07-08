package gg.modl.backend.staff.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.auth.WebAuthnService;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.database.mongo.repository.InvitationMongoRepository;
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
import gg.modl.backend.staff.dto.response.StaffResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StaffServiceSuperAdminAssignmentTest {

    private static final String ADMIN_EMAIL = "owner@example.com";
    private static final RoleAuthorization.PerformerAuthority SUPER_ADMIN =
        new RoleAuthorization.PerformerAuthority(ADMIN_EMAIL, null, true, true);

    private StaffMongoRepository staffRepository;
    private PlayerMongoRepository playerRepository;
    private StaffService service;
    private Server server;

    @BeforeEach
    void setUp() {
        staffRepository = mock(StaffMongoRepository.class);
        playerRepository = mock(PlayerMongoRepository.class);
        service = new StaffService(
            mock(InvitationMongoRepository.class),
            staffRepository,
            playerRepository,
            mock(PunishmentMongoRepository.class),
            mock(PlayerService.class),
            mock(PermissionService.class),
            mock(RoleAuthorization.class),
            mock(ServerTimestampService.class),
            mock(WebAuthnService.class),
            mock(SessionService.class)
        );
        server = new Server("Server", "server", "server_db", ADMIN_EMAIL, true, ServerPlan.FREE);
        server.setId("server-id");
        server.setCreatedAt(new Date(1_700_000_000_000L));
    }

    @Test
    void assignForAdminEmailWithoutStaffRecordMaterializesOwnerStaffRecord() {
        UUID playerUuid = UUID.fromString("dfc1d5fe-106e-47b2-93b0-f2a9b0dc1f86");
        Player player = Player.builder()
            .minecraftUuid(playerUuid)
            .usernames(new ArrayList<>(List.of(new UsernameEntry("byteful", new Date()))))
            .build();
        when(staffRepository.findByEmailIgnoreCase(server, ADMIN_EMAIL)).thenReturn(Optional.empty());
        when(playerRepository.findByMinecraftUuid(server, playerUuid.toString())).thenReturn(Optional.of(player));
        when(staffRepository.findByAssignedMinecraftUuidExcludingId(any(), any(), any())).thenReturn(Optional.empty());
        when(staffRepository.saveEntity(any(Server.class), any(Staff.class))).thenAnswer(inv -> inv.getArgument(1));

        Optional<StaffResponse> result = service.assignMinecraftPlayer(server, ADMIN_EMAIL,
            new AssignMinecraftPlayerRequest(playerUuid.toString(), null), SUPER_ADMIN);

        assertTrue(result.isPresent());
        ArgumentCaptor<Staff> saved = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepository).saveEntity(any(Server.class), saved.capture());
        assertEquals(ADMIN_EMAIL, saved.getValue().getEmail());
        assertEquals("Admin", saved.getValue().getUsername());
        assertEquals(RoleAuthorization.SUPER_ADMIN_ROLE_ID, saved.getValue().getRoleId());
        assertEquals(playerUuid.toString(), saved.getValue().getAssignedMinecraftUuid());
        assertEquals("byteful", saved.getValue().getAssignedMinecraftUsername());
    }

    @Test
    void clearForUnsavedOwnerReturnsSuccessWithoutPersisting() {
        when(staffRepository.findByEmailIgnoreCase(server, ADMIN_EMAIL)).thenReturn(Optional.empty());

        Optional<StaffResponse> result = service.assignMinecraftPlayer(server, ADMIN_EMAIL,
            new AssignMinecraftPlayerRequest(null, null), SUPER_ADMIN);

        assertTrue(result.isPresent());
        verify(staffRepository, never()).saveEntity(any(Server.class), any(Staff.class));
    }

    @Test
    void assignForUnknownEmailReturnsEmpty() {
        when(staffRepository.findByEmailIgnoreCase(server, "ghost@example.com")).thenReturn(Optional.empty());

        Optional<StaffResponse> result = service.assignMinecraftPlayer(server, "ghost@example.com",
            new AssignMinecraftPlayerRequest(null, null), SUPER_ADMIN);

        assertTrue(result.isEmpty());
    }

    @Test
    void clearForExistingOwnerRecordPersistsClearedAssignment() {
        Staff ownerRecord = Staff.builder()
            .id("owner-id")
            .email(ADMIN_EMAIL)
            .username("Admin")
            .roleId(RoleAuthorization.SUPER_ADMIN_ROLE_ID)
            .assignedMinecraftUuid("dfc1d5fe-106e-47b2-93b0-f2a9b0dc1f86")
            .assignedMinecraftUsername("byteful")
            .build();
        when(staffRepository.findByEmailIgnoreCase(server, ADMIN_EMAIL)).thenReturn(Optional.of(ownerRecord));
        when(staffRepository.saveEntity(any(Server.class), any(Staff.class))).thenAnswer(inv -> inv.getArgument(1));

        Optional<StaffResponse> result = service.assignMinecraftPlayer(server, ADMIN_EMAIL,
            new AssignMinecraftPlayerRequest(null, null), SUPER_ADMIN);

        assertTrue(result.isPresent());
        ArgumentCaptor<Staff> saved = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepository).saveEntity(any(Server.class), saved.capture());
        assertNull(saved.getValue().getAssignedMinecraftUuid());
        assertNull(saved.getValue().getAssignedMinecraftUsername());
    }
}
