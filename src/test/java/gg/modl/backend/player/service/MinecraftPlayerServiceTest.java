package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.dto.request.AcknowledgeNotificationsRequest;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.PunishmentTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private MojangApiService mojangApiService;

    @Mock
    private IssuerNameResolver issuerNameResolver;

    @Mock
    private TenantMongoAccess tenantMongoAccess;

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
                mojangApiService,
                issuerNameResolver,
                tenantMongoAccess
        );
    }

    @Test
    void getPlayerByMinecraftUuidFallsBackToMojang() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        UUID playerUuid = UUID.randomUUID();

        when(playerRepository.findByMinecraftUuid(server, playerUuid.toString())).thenReturn(Optional.empty());
        when(mojangApiService.lookupByUuid(playerUuid.toString()))
                .thenReturn(Optional.of(new MojangApiService.MojangProfile("LookupName", playerUuid)));

        MinecraftPlayerService.ServiceResponse response = minecraftPlayerService.getPlayerByMinecraftUuid(
                server,
                playerUuid.toString(),
                true
        );

        assertEquals(org.springframework.http.HttpStatus.OK, response.status());
        assertEquals("Player found via Mojang", response.body().get("message"));
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> remainingNotifications(Player player) {
        return (List<Map<String, Object>>) player.getData().get("pendingNotifications");
    }
}


