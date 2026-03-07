package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.dto.request.CreatePunishmentRequest;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.storage.service.EvidenceUploadTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PunishmentServiceTest {

    @Mock
    private PlayerMongoRepository playerRepository;

    @Mock
    private TicketMongoRepository ticketRepository;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private OffenderThresholdSettingsService thresholdSettingsService;

    @Mock
    private EvidenceUploadTokenService evidenceUploadTokenService;

    @Mock
    private IssuerNameResolver issuerNameResolver;

    @Mock
    private TenantMongoAccess tenantMongoAccess;

    private PunishmentService punishmentService;

    @BeforeEach
    void setUp() {
        punishmentService = new PunishmentService(
                playerRepository,
                ticketRepository,
                statusCalculator,
                punishmentTypeService,
                thresholdSettingsService,
                evidenceUploadTokenService,
                issuerNameResolver,
                tenantMongoAccess
        );
    }

    @Test
    void acknowledgePunishmentStartsPunishmentAndClearsQueuedStatus() {
        UUID playerUuid = UUID.randomUUID();
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        Punishment punishment = new Punishment(
                "punish-1",
                1,
                "Mod",
                null,
                new Date(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new HashMap<>(Map.of("status", "Unstarted"))
        );
        Player player = Player.builder()
                .minecraftUuid(playerUuid)
                .punishments(new ArrayList<>(List.of(punishment)))
                .build();

        when(playerRepository.findOne(any(Server.class), any(Query.class))).thenReturn(Optional.of(player));
        when(playerRepository.snapshot(any(Player.class))).thenReturn(Player.builder().minecraftUuid(playerUuid).build());

        PunishmentService.PunishmentOperationResult result = punishmentService.acknowledgePunishment(
                server,
                playerUuid,
                "punish-1"
        );

        assertEquals(PunishmentService.PunishmentOperationStatus.SUCCESS, result.status());
        assertEquals("Punishment acknowledged", result.message());

        ArgumentCaptor<Player> updatedPlayerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).saveChanges(any(Server.class), any(Player.class), updatedPlayerCaptor.capture());
        Punishment updatedPunishment = updatedPlayerCaptor.getValue().getPunishments().get(0);
        assertNotNull(updatedPunishment.getStarted());
        assertNull(updatedPunishment.getData().get("status"));
    }

    @Test
    void toggleOptionRejectsUnknownOption() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        PunishmentService.PunishmentOperationResult result = punishmentService.toggleOption(
                server,
                "punish-1",
                "UNKNOWN_OPTION",
                true,
                "Mod",
                null
        );

        assertEquals(PunishmentService.PunishmentOperationStatus.INVALID_REQUEST, result.status());
        assertEquals("Invalid option", result.message());
    }

    @Test
    void createPunishmentMutatesPlayerAggregateAndPersistsThroughRepository() {
        UUID playerUuid = UUID.randomUUID();
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        Player player = Player.builder()
                .minecraftUuid(playerUuid)
                .usernames(new ArrayList<>(List.of(new UsernameEntry("CurrentName", new Date()))))
                .punishments(new ArrayList<>())
                .data(new HashMap<>(Map.of("lastSkinHash", "skin-hash")))
                .build();

        CreatePunishmentRequest request = new CreatePunishmentRequest(
                "Mod",
                null,
                4,
                null,
                null,
                null,
                null,
                null,
                new HashMap<>(Map.of("status", "Queued")),
                "Reason text",
                null
        );

        when(playerRepository.findOne(eq(server), any(Query.class))).thenReturn(Optional.of(player));
        when(playerRepository.snapshot(player)).thenReturn(Player.builder().minecraftUuid(playerUuid).punishments(new ArrayList<>()).build());

        String punishmentId = punishmentService.createPunishment(server, playerUuid, request);

        assertNotNull(punishmentId);

        ArgumentCaptor<Player> updatedPlayerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).saveChanges(eq(server), any(Player.class), updatedPlayerCaptor.capture());
        Player updatedPlayer = updatedPlayerCaptor.getValue();
        assertEquals(1, updatedPlayer.getPunishments().size());
        Punishment createdPunishment = updatedPlayer.getPunishments().get(0);
        assertEquals(punishmentId, createdPunishment.getId());
        assertEquals("Reason text", createdPunishment.getData().get("reason"));
        assertEquals("Queued", createdPunishment.getData().get("status"));
    }

    @Test
    void systemPardonPunishmentAddsSystemPardonThroughRepositorySave() {
        UUID playerUuid = UUID.randomUUID();
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        Punishment punishment = new Punishment(
                "punish-1",
                1,
                "Mod",
                null,
                new Date(),
                new Date(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>()
        );
        Player player = Player.builder()
                .minecraftUuid(playerUuid)
                .punishments(new ArrayList<>(List.of(punishment)))
                .build();

        when(playerRepository.findOne(eq(server), any(Query.class))).thenReturn(Optional.of(player));
        when(playerRepository.snapshot(player)).thenReturn(Player.builder().minecraftUuid(playerUuid).punishments(new ArrayList<>()).build());

        punishmentService.systemPardonPunishment(server, playerUuid, "punish-1", "Auto-pardoned");

        ArgumentCaptor<Player> updatedPlayerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).saveChanges(eq(server), any(Player.class), updatedPlayerCaptor.capture());
        Punishment updatedPunishment = updatedPlayerCaptor.getValue().getPunishments().get(0);
        assertEquals(1, updatedPunishment.getModifications().size());
        PunishmentModification modification = updatedPunishment.getModifications().get(0);
        assertEquals("SYSTEM_PARDON", modification.type());
        assertEquals("Auto-pardoned", modification.reason());
        assertEquals("Auto-pardoned", updatedPunishment.getNotes().get(0).text());
    }

    @Test
    void cascadePardonLinkedBansUsesDatabaseScopedRepositorySaves() {
        UUID playerUuid = UUID.randomUUID();
        Punishment linkedBan = new Punishment(
                "linked-1",
                4,
                "System",
                null,
                new Date(),
                new Date(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(Map.of("linkedBanId", "parent-1"))
        );
        Player player = Player.builder()
                .minecraftUuid(playerUuid)
                .punishments(new ArrayList<>(List.of(linkedBan)))
                .build();

        when(playerRepository.find(eq("db"), any(Query.class))).thenReturn(List.of(player));
        when(playerRepository.snapshot(player)).thenReturn(Player.builder().minecraftUuid(playerUuid).punishments(new ArrayList<>()).build());
        when(statusCalculator.isPunishmentActive(linkedBan)).thenReturn(true);

        int updatedCount = punishmentService.cascadePardonLinkedBans("db", "parent-1");

        assertEquals(1, updatedCount);

        ArgumentCaptor<Player> updatedPlayerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).saveChanges(eq("db"), any(Player.class), updatedPlayerCaptor.capture());
        Punishment updatedPunishment = updatedPlayerCaptor.getValue().getPunishments().get(0);
        assertEquals("SYSTEM_PARDON", updatedPunishment.getModifications().get(0).type());
    }

    @Test
    void cascadePardonLinkedBansSkipsInactiveLinkedBan() {
        UUID playerUuid = UUID.randomUUID();
        Punishment linkedBan = new Punishment(
                "linked-1",
                4,
                "System",
                null,
                new Date(),
                new Date(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(Map.of("linkedBanId", "parent-1"))
        );
        Player player = Player.builder()
                .minecraftUuid(playerUuid)
                .punishments(new ArrayList<>(List.of(linkedBan)))
                .build();

        when(playerRepository.find(eq("db"), any(Query.class))).thenReturn(List.of(player));
        when(playerRepository.snapshot(player)).thenReturn(Player.builder().minecraftUuid(playerUuid).punishments(new ArrayList<>()).build());
        when(statusCalculator.isPunishmentActive(linkedBan)).thenReturn(false);

        int updatedCount = punishmentService.cascadePardonLinkedBans("db", "parent-1");

        assertEquals(0, updatedCount);
        verify(playerRepository, never()).saveChanges(eq("db"), any(Player.class), any(Player.class));
    }
}
