package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.MigrationMongoRepository;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerInstanceSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffRoleMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinecraftSyncServiceTest {

    @Mock
    private PlayerMongoRepository playerRepository;

    @Mock
    private StaffMongoRepository staffRepository;

    @Mock
    private StaffRoleMongoRepository staffRoleRepository;

    @Mock
    private TicketMongoRepository ticketRepository;

    @Mock
    private ServerMongoRepository serverRepository;

    @Mock
    private MigrationMongoRepository migrationRepository;

    @Mock
    private ServerInstanceSnapshotMongoRepository serverInstanceSnapshotRepository;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private PunishmentLifecycleService punishmentLifecycleService;

    @Mock
    private MinecraftChatLogService minecraftChatLogService;

    @Mock
    private IssuerNameResolver issuerNameResolver;

    @Mock
    private ModlProperties modlProperties;

    private MinecraftSyncService minecraftSyncService;

    @BeforeEach
    void setUp() {
        minecraftSyncService = new MinecraftSyncService(
            modlProperties,
            playerRepository,
            staffRepository,
            staffRoleRepository,
            ticketRepository,
            serverRepository,
            migrationRepository,
            serverInstanceSnapshotRepository,
            statusCalculator,
            punishmentTypeService,
            punishmentLifecycleService,
            minecraftChatLogService,
            issuerNameResolver
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void syncReturnsEnvelopeWhenNoPlayersAreOnline() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        when(punishmentTypeService.getPunishmentTypes(server)).thenReturn(List.of());

        Map<String, Object> response = minecraftSyncService.sync(
            server,
            "2025-01-01T00:00:00Z",
            List.of(),
            "lobby",
            List.of(),
            List.of(),
            null,
            null
        );

        assertNotNull(response.get("timestamp"));
        assertTrue(response.containsKey("data"));
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertTrue(data.containsKey("pendingPunishments"));
        assertTrue(data.containsKey("staffNotifications"));
    }
}
