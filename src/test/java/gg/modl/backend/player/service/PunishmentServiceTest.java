package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.ticket.service.TicketService;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.dto.request.CreatePunishmentRequest;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.settings.service.WebhookSettingsService;
import gg.modl.backend.log.service.LogService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PunishmentServiceTest {

    @Mock
    private PlayerMongoRepository playerRepository;

    @Mock
    private PunishmentMongoRepository punishmentRepository;

    @Mock
    private TicketService ticketService;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private OffenderThresholdSettingsService thresholdSettingsService;

    @Mock
    private PunishmentDurationCalculator durationCalculator;

    @Mock
    private IssuerNameResolver issuerNameResolver;

    @Mock
    private StaffMongoRepository staffRepository;

    @Mock
    private PunishmentQueryService punishmentQueryService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private WebhookSettingsService webhookSettingsService;

    @Mock
    private PunishmentRealtimePublisher realtimePublisher;

    @Mock
    private LogService logService;

    private PunishmentLifecycleService punishmentLifecycleService;

    private PunishmentMutationService punishmentMutationService;

    @BeforeEach
    void setUp() {
        punishmentLifecycleService = new PunishmentLifecycleService(
            playerRepository,
            punishmentRepository,
            ticketService,
            statusCalculator,
            punishmentTypeService,
            thresholdSettingsService,
            durationCalculator,
            issuerNameResolver,
            staffRepository,
            punishmentQueryService,
            permissionService,
            webhookSettingsService,
            realtimePublisher,
            logService
        );
        punishmentMutationService = new PunishmentMutationService(
            playerRepository,
            punishmentRepository,
            ticketService,
            issuerNameResolver,
            staffRepository,
            punishmentQueryService,
            punishmentLifecycleService,
            realtimePublisher
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

        when(playerRepository.findByMinecraftUuid(server, playerUuid.toString())).thenReturn(Optional.of(player));

        PunishmentOperationResult result = punishmentLifecycleService.acknowledgePunishment(
            server,
            playerUuid,
            "punish-1"
        );

        assertEquals(PunishmentOperationStatus.SUCCESS, result.status());
        assertEquals("Punishment acknowledged", result.message());
        verify(punishmentRepository).replacePunishments(eq(server), eq(player));
        Punishment updatedPunishment = player.getPunishments().get(0);
        assertNotNull(updatedPunishment.getStarted());
        assertNull(updatedPunishment.getData().get("status"));
    }

    @Test
    void toggleOptionRejectsUnknownOption() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        PunishmentOperationResult result = punishmentMutationService.toggleOption(
            server,
            "punish-1",
            "UNKNOWN_OPTION",
            true,
            "Mod",
            null
        );

        assertEquals(PunishmentOperationStatus.INVALID_REQUEST, result.status());
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

        when(playerRepository.findByMinecraftUuid(server, playerUuid.toString())).thenReturn(Optional.of(player));
        when(issuerNameResolver.resolve(any(), any(), any(Server.class))).thenReturn("Mod");

        String punishmentId = punishmentLifecycleService.createPunishment(server, playerUuid, request);

        assertNotNull(punishmentId);
        verify(punishmentRepository).replacePunishments(eq(server), eq(player));
        assertEquals(1, player.getPunishments().size());
        Punishment createdPunishment = player.getPunishments().get(0);
        assertEquals(punishmentId, createdPunishment.getId());
        assertEquals("Reason text", createdPunishment.getData().get("reason"));
        assertEquals("Queued", createdPunishment.getData().get("status"));
    }

    @Test
    void createPunishmentPersistsInternalOffenseLevelThatSurvivesStatusWipe() {
        UUID playerUuid = UUID.randomUUID();
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        Player player = Player.builder()
            .minecraftUuid(playerUuid)
            .usernames(new ArrayList<>(List.of(new UsernameEntry("CurrentName", new Date()))))
            .punishments(new ArrayList<>())
            .data(new HashMap<>())
            .build();

        CreatePunishmentRequest request = new CreatePunishmentRequest(
            "Mod", null, 6, null, null, null,
            "regular", null, new HashMap<>(), "Reason text", null
        );

        when(playerRepository.findByMinecraftUuid(server, playerUuid.toString())).thenReturn(Optional.of(player));
        when(issuerNameResolver.resolve(any(), any(), any(Server.class))).thenReturn("Mod");
        when(durationCalculator.calculate(eq(server), any(), eq(6), eq("regular")))
            .thenReturn(new PunishmentDurationCalculator.DurationResult(3600_000L, "habitual", "habitual"));

        punishmentLifecycleService.createPunishment(server, playerUuid, request);

        Punishment created = player.getPunishments().get(0);
        assertEquals("habitual", created.getData().get("offenseLevel"));

        // Simulate a status-wipe lifecycle mutation; the dedicated offenseLevel must survive.
        created.getData().remove("status");
        assertEquals("habitual", created.getData().get("offenseLevel"));
    }

    @Test
    void createMinecraftPunishmentForcesUnstartedForNonStackingPluginPunishment() {
        UUID playerUuid = UUID.randomUUID();
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        Player player = Player.builder()
            .minecraftUuid(playerUuid)
            .usernames(new ArrayList<>(List.of(new UsernameEntry("CurrentName", new Date()))))
            .punishments(new ArrayList<>())
            .data(new HashMap<>())
            .build();

        CreatePunishmentRequest request = new CreatePunishmentRequest(
            "Mod", null, 2, null, null, null, null, null,
            new HashMap<>(Map.of("pendingAcknowledgement", true)), "Reason text", null
        );

        when(playerRepository.findByMinecraftUuid(server, playerUuid.toString())).thenReturn(Optional.of(player));
        when(issuerNameResolver.resolve(any(), any(), any(Server.class))).thenReturn("Mod");

        punishmentLifecycleService.createPunishment(server, playerUuid, request);

        Punishment created = player.getPunishments().get(0);
        assertEquals("Unstarted", created.getData().get("status"));
        assertNull(created.getStarted());
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

        when(playerRepository.findByMinecraftUuid(server, playerUuid.toString())).thenReturn(Optional.of(player));

        punishmentLifecycleService.systemPardonPunishment(server, playerUuid, "punish-1", "Auto-pardoned");

        verify(punishmentRepository).replacePunishments(eq(server), eq(player));
        Punishment updatedPunishment = player.getPunishments().get(0);
        assertEquals(1, updatedPunishment.getModifications().size());
        PunishmentModification modification = updatedPunishment.getModifications().get(0);
        assertEquals("SYSTEM_PARDON", modification.type());
        assertEquals("Auto-pardoned", modification.reason());
        assertEquals("Auto-pardoned", updatedPunishment.getNotes().get(0).text());
        assertEquals("Pardoned", updatedPunishment.getData().get("status"));
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

        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        when(punishmentRepository.findByLinkedBanId(server, "parent-1")).thenReturn(List.of(player));
        when(statusCalculator.isPunishmentActive(linkedBan)).thenReturn(true);

        int updatedCount = punishmentLifecycleService.cascadePardonLinkedBans(server, "parent-1");

        assertEquals(1, updatedCount);
        verify(punishmentRepository).replacePunishments(eq(server), eq(player));
        Punishment updatedPunishment = player.getPunishments().get(0);
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

        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        when(punishmentRepository.findByLinkedBanId(server, "parent-1")).thenReturn(List.of(player));
        when(statusCalculator.isPunishmentActive(linkedBan)).thenReturn(false);

        int updatedCount = punishmentLifecycleService.cascadePardonLinkedBans(server, "parent-1");

        assertEquals(0, updatedCount);
        verify(punishmentRepository, never()).replacePunishments(eq(server), any(Player.class));
    }
}
