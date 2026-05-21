package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.dto.request.AcknowledgeNotificationsRequest;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
class MinecraftPlayerServiceTest {

    @Mock
    private PlayerService playerService;

    @Mock
    private PlayerMongoRepository playerRepository;

    @Mock
    private TicketMongoRepository ticketRepository;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private PunishmentLifecycleService punishmentLifecycleService;

    @Mock
    private AccountLinkingService accountLinkingService;

    @Mock
    private IssuerNameResolver issuerNameResolver;

    @Mock
    private StaffMongoRepository staffRepository;

    private MinecraftPlayerService minecraftPlayerService;

    @BeforeEach
    void setUp() {
        minecraftPlayerService = new MinecraftPlayerService(
            playerService,
            playerRepository,
            ticketRepository,
            statusCalculator,
            punishmentTypeService,
            punishmentLifecycleService,
            accountLinkingService,
            issuerNameResolver,
            staffRepository
        );
    }

    @Test
    void createNotePersistsThroughRepositorySaveChanges() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        Player player = Player.builder()
            .minecraftUuid(UUID.randomUUID())
            .build();

        when(playerRepository.findByMinecraftUuid(server, player.getMinecraftUuid().toString())).thenReturn(Optional.of(player));

        MinecraftPlayerService.ServiceResponse response = minecraftPlayerService.createNote(
            server,
            player.getMinecraftUuid().toString(),
            "Test note",
            "Moderator",
            null
        );

        assertEquals(org.springframework.http.HttpStatus.OK, response.status());
        verify(playerRepository).replaceNotes(server, player);
        assertEquals("Test note", player.getNotes().get(0).getText());
    }

    @Test
    void acknowledgeNotificationsRemovesOnlyRequestedNotificationIds() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        UUID playerUuid = UUID.randomUUID();
        Player player = Player.builder()
            .minecraftUuid(playerUuid)
            .data(new LinkedHashMap<>(Map.of(
                "pendingNotifications", new ArrayList<>(List.of(
                    new LinkedHashMap<>(Map.of("id", "notif-1", "message", "one")),
                    new LinkedHashMap<>(Map.of("id", "notif-2", "message", "two"))
                ))
            )))
            .build();

        when(playerRepository.findByMinecraftUuid(server, playerUuid.toString())).thenReturn(Optional.of(player));

        MinecraftPlayerService.ServiceResponse response = minecraftPlayerService.acknowledgeNotifications(
            server,
            new AcknowledgeNotificationsRequest(playerUuid.toString(), List.of("notif-1"), null)
        );

        assertEquals(org.springframework.http.HttpStatus.OK, response.status());
        verify(playerRepository).replacePendingNotifications(server, player, remainingNotifications(player));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> remaining = (List<Map<String, Object>>) player.getData().get("pendingNotifications");
        assertEquals(1, remaining.size());
        assertEquals("notif-2", remaining.get(0).get("id"));
    }

    @Test
    void getPlayerReportsLowercasesUuidBeforeQueryingTicketRepository() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        when(ticketRepository.findReportedPlayerTickets(any(Server.class), any(), anyInt())).thenReturn(List.of());

        minecraftPlayerService.getPlayerReports(server, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE");

        verify(ticketRepository).findReportedPlayerTickets(server, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", 50);
    }

    @Test
    void disconnectLowercasesUuidBeforeQueryingPlayerRepository() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        minecraftPlayerService.disconnect(server, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", 5_000L);

        verify(playerRepository).markDisconnected(eq(server), eq("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), anyLong());
    }

    @Test
    void updateServerLowercasesUuidBeforeQueryingPlayerRepository() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        minecraftPlayerService.updateServer(server, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", "lobby");

        verify(playerRepository).updateLastServer(server, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", "lobby");
    }

    @Test
    void submitIpInfoLowercasesUuidBeforeForwardingToPlayerService() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        minecraftPlayerService.submitIpInfo(server, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE",
            "1.2.3.4", "US", "CA", "ASN", false, false);

        verify(playerService).updateIpGeoData(eq(server), eq("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), eq("1.2.3.4"), any());
    }

    @Test
    void getPlayerPunishmentsLowercasesUuidBeforeQueryingPlayerRepository() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        when(playerRepository.findByMinecraftUuid(eq(server), eq("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")))
            .thenReturn(Optional.empty());

        minecraftPlayerService.getPlayerPunishments(server, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", 1, 10);

        verify(playerRepository).findByMinecraftUuid(server, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> remainingNotifications(Player player) {
        return (List<Map<String, Object>>) player.getData().get("pendingNotifications");
    }
}


