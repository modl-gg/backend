package gg.modl.backend.database.mongo;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MongoInfrastructureTest {

    @Test
    void resolvesMappedFieldNamesForRootAndNestedFields() {
        assertEquals("_id", MongoFieldNames.field(Server.class, Server::getId).path());
        assertEquals("customDomainOverride", MongoFieldNames.field(Server.class, Server::getCustomDomainOverride).path());
        assertEquals("punishments.id", MongoFieldNames.field(Player.class, Player::getPunishments, Punishment.class, Punishment::getId).path());
        assertEquals("data.isOnline", MongoFieldNames.raw(Player.class, MongoFieldNames.field(Player.class, Player::getData).path() + ".isOnline").path());
    }

    @Test
    void diffTracksOnlyChangedTopLevelFields() {
        MongoEntityDiffService diffService = createDiffService();

        Server original = new Server("Alpha", "alpha", "server_alpha", "admin@example.com", false, ServerPlan.FREE);
        original.setId("507f1f77bcf86cd799439011");
        original.setProvisioningStatus(ProvisioningStatus.PENDING);
        original.setUpdatedAt(new Date(1_000));

        Server updated = diffService.snapshot(original, Server.class);
        updated.setProvisioningStatus(ProvisioningStatus.COMPLETED);
        updated.setUpdatedAt(new Date(2_000));

        MongoEntityUpdatePlan updatePlan = diffService.diff(original, updated);

        assertTrue(updatePlan.hasChanges());
        assertEquals(2, updatePlan.setOperations().size());
        assertEquals("COMPLETED", updatePlan.setOperations().get("provisioningStatus"));
        assertEquals(new Date(2_000), updatePlan.setOperations().get("updatedAt"));
        assertFalse(updatePlan.setOperations().containsKey("_id"));
        assertTrue(updatePlan.unsetOperations().isEmpty());
    }

    @Test
    void diffTracksNestedChangesWithoutReplacingWholeDocument() {
        MongoEntityDiffService diffService = createDiffService();

        SystemConfig original = new SystemConfig();
        original.setId("507f191e810c19729de860ea");
        original.setUpdatedAt(new Date(1_000));

        SystemConfig updated = diffService.snapshot(original, SystemConfig.class);
        updated.getGeneral().setMaintenanceMode(true);
        updated.getPerformance().setRateLimitRequests(250);
        updated.setUpdatedAt(new Date(2_000));

        MongoEntityUpdatePlan updatePlan = diffService.diff(original, updated);

        assertEquals(Boolean.TRUE, updatePlan.setOperations().get("general.maintenanceMode"));
        assertEquals(250, updatePlan.setOperations().get("performance.rateLimitRequests"));
        assertEquals(new Date(2_000), updatePlan.setOperations().get("updatedAt"));
        assertFalse(updatePlan.setOperations().containsKey("general"));
    }

    @Test
    void diffUnsetsRemovedFields() {
        MongoEntityDiffService diffService = createDiffService();

        Server original = new Server("Alpha", "alpha", "server_alpha", "admin@example.com", false, ServerPlan.FREE);
        original.setId(UUID.randomUUID().toString());
        original.setProvisioningNotes("waiting");

        Server updated = diffService.snapshot(original, Server.class);
        updated.setProvisioningNotes(null);

        MongoEntityUpdatePlan updatePlan = diffService.diff(original, updated);

        assertTrue(updatePlan.unsetOperations().contains("provisioningNotes"));
        assertFalse(updatePlan.setOperations().containsKey("provisioningNotes"));
    }

    private MongoEntityDiffService createDiffService() {
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.afterPropertiesSet();

        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        converter.afterPropertiesSet();
        return new MongoEntityDiffService(converter);
    }
}
