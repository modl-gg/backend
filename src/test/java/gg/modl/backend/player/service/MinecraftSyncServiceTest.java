package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.PunishmentTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinecraftSyncServiceTest {

    @Mock
    private TenantMongoAccess tenantMongoAccess;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private PunishmentService punishmentService;

    @Mock
    private MinecraftChatLogService minecraftChatLogService;

    @Mock
    private MongoTemplate mongoTemplate;

    private MinecraftSyncService minecraftSyncService;

    @BeforeEach
    void setUp() {
        minecraftSyncService = new MinecraftSyncService(
                tenantMongoAccess,
                statusCalculator,
                punishmentTypeService,
                punishmentService,
                minecraftChatLogService
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void syncReturnsEnvelopeWhenNoPlayersAreOnline() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        when(tenantMongoAccess.forServer(server)).thenReturn(mongoTemplate);
        when(punishmentTypeService.getPunishmentTypes(server)).thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), any(Class.class), anyString())).thenReturn((List) List.of());
        when(mongoTemplate.findOne(any(Query.class), any(Class.class), anyString())).thenReturn(null);

        Map<String, Object> response = minecraftSyncService.sync(
                server,
                "2025-01-01T00:00:00Z",
                List.of(),
                "lobby",
                List.of(),
                List.of()
        );

        assertNotNull(response.get("timestamp"));
        assertTrue(response.containsKey("data"));
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertTrue(data.containsKey("pendingPunishments"));
        assertTrue(data.containsKey("staffNotifications"));
    }
}
