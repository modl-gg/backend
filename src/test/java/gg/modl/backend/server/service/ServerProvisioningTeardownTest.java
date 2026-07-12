package gg.modl.backend.server.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.MongoDatabase;
import gg.modl.backend.database.MongoIndexReconciler;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.repository.HomepageCardMongoRepository;
import gg.modl.backend.database.mongo.repository.KnowledgebaseCategoryMongoRepository;
import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.role.service.RoleService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class ServerProvisioningTeardownTest {

    private TenantMongoAccess tenantMongoAccess;
    private ServerProvisioningService service;
    private Server server;

    @BeforeEach
    void setUp() {
        tenantMongoAccess = mock(TenantMongoAccess.class);
        service = new ServerProvisioningService(
            tenantMongoAccess,
            mock(MongoIndexReconciler.class),
            mock(SettingsMongoRepository.class),
            mock(KnowledgebaseCategoryMongoRepository.class),
            mock(HomepageCardMongoRepository.class),
            mock(RoleService.class)
        );
        server = new Server("Server", "server", "server_db", "owner@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
    }

    @Test
    void teardownDropsDatabaseCreatedByThisAttempt() {
        MongoTemplate template = mock(MongoTemplate.class);
        MongoDatabase database = mock(MongoDatabase.class);
        when(tenantMongoAccess.forServer(server)).thenReturn(template);
        when(template.getDb()).thenReturn(database);

        service.teardownProvisionedDatabase(server, false);

        verify(database).drop();
    }

    @Test
    void teardownLeavesPreExistingDatabaseUntouched() {
        service.teardownProvisionedDatabase(server, true);

        verifyNoInteractions(tenantMongoAccess);
    }
}
