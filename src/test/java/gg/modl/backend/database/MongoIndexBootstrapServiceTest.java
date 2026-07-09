package gg.modl.backend.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.MongoIndexBootstrapService.BootstrapTarget;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;

class MongoIndexBootstrapServiceTest {
    @Test
    void initGlobalIndexesSkipsEquivalentLegacyIndexWithDifferentName() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        IndexOperations servers = mock(IndexOperations.class);
        IndexOperations metrics = mock(IndexOperations.class);
        IndexOperations replayLite = mock(IndexOperations.class);
        IndexOperations replayLiteQuotas = mock(IndexOperations.class);
        IndexOperations adminUsers = mock(IndexOperations.class);
        IndexOperations systemAlerts = mock(IndexOperations.class);

        when(tenantMongoAccess.global()).thenReturn(template);
        when(template.indexOps(CollectionName.MODL_SERVERS)).thenReturn(servers);
        when(template.indexOps(CollectionName.METRIC_SNAPSHOTS)).thenReturn(metrics);
        when(template.indexOps(CollectionName.REPLAY_LITE_REPLAYS)).thenReturn(replayLite);
        when(template.indexOps(CollectionName.REPLAY_LITE_DAILY_QUOTAS)).thenReturn(replayLiteQuotas);
        when(template.indexOps("admin_users")).thenReturn(adminUsers);
        when(template.indexOps(CollectionName.SYSTEM_ALERTS)).thenReturn(systemAlerts);

        when(servers.getIndexInfo()).thenReturn(List.of());
        when(metrics.getIndexInfo()).thenReturn(List.of());
        when(replayLite.getIndexInfo()).thenReturn(List.of());
        when(replayLiteQuotas.getIndexInfo()).thenReturn(List.of());
        when(systemAlerts.getIndexInfo()).thenReturn(List.of());
        when(adminUsers.getIndexInfo()).thenReturn(List.of(IndexInfo.indexInfoOf(
            new Document("name", "email_1")
                .append("key", new Document("email", 1))
                .append("unique", true)
        )));

        MongoIndexBootstrapService service = new MongoIndexBootstrapService(tenantMongoAccess, mock(TenantMigrationService.class));
        service.initGlobalIndexes();

        verify(adminUsers, never()).createIndex(any());
        ArgumentCaptor<IndexDefinition> serverIndexCaptor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(servers, atLeastOnce()).createIndex(serverIndexCaptor.capture());
        assertThat(serverIndexCaptor.getAllValues()).anySatisfy(index -> {
            assertThat(index.getIndexOptions().getString("name")).isEqualTo("idx_servers_registration_cleanup");
            assertThat(index.getIndexKeys()).isEqualTo(new Document("emailVerified", 1)
                .append("provisioningStatus", 1)
                .append("createdAt", 1)
                .append("emailVerificationToken", 1));
        });
        verify(metrics, atLeastOnce()).createIndex(any());
        verify(replayLite, atLeastOnce()).createIndex(any());
        verify(replayLiteQuotas, atLeastOnce()).createIndex(any());
    }

    @Test
    void initGlobalIndexesCreatesAdminIndexWhenLegacyIndexOptionsDiffer() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        IndexOperations servers = mock(IndexOperations.class);
        IndexOperations metrics = mock(IndexOperations.class);
        IndexOperations replayLite = mock(IndexOperations.class);
        IndexOperations replayLiteQuotas = mock(IndexOperations.class);
        IndexOperations adminUsers = mock(IndexOperations.class);
        IndexOperations systemAlerts = mock(IndexOperations.class);

        when(tenantMongoAccess.global()).thenReturn(template);
        when(template.indexOps(CollectionName.MODL_SERVERS)).thenReturn(servers);
        when(template.indexOps(CollectionName.METRIC_SNAPSHOTS)).thenReturn(metrics);
        when(template.indexOps(CollectionName.REPLAY_LITE_REPLAYS)).thenReturn(replayLite);
        when(template.indexOps(CollectionName.REPLAY_LITE_DAILY_QUOTAS)).thenReturn(replayLiteQuotas);
        when(template.indexOps("admin_users")).thenReturn(adminUsers);
        when(template.indexOps(CollectionName.SYSTEM_ALERTS)).thenReturn(systemAlerts);

        when(servers.getIndexInfo()).thenReturn(List.of());
        when(metrics.getIndexInfo()).thenReturn(List.of());
        when(replayLite.getIndexInfo()).thenReturn(List.of());
        when(replayLiteQuotas.getIndexInfo()).thenReturn(List.of());
        when(systemAlerts.getIndexInfo()).thenReturn(List.of());
        when(adminUsers.getIndexInfo()).thenReturn(List.of(IndexInfo.indexInfoOf(
            new Document("name", "email_1")
                .append("key", new Document("email", 1))
        )));

        MongoIndexBootstrapService service = new MongoIndexBootstrapService(tenantMongoAccess, mock(TenantMigrationService.class));
        service.initGlobalIndexes();

        verify(adminUsers).createIndex(any());
        IndexDefinition index = captureCreatedIndex(adminUsers);
        assertThat(index.getIndexKeys()).isEqualTo(new Document("email", 1));
        assertThat(index.getIndexOptions().getString("name")).isEqualTo("uidx_admin_users_email");
        assertThat(index.getIndexOptions().getBoolean("unique")).isTrue();
    }

    @Test
    void createTenantIndexesCreatesLegacyReplayLookupIndexes() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        IndexOperations replays = mock(IndexOperations.class);
        stubTenantIndexOps(template);
        when(template.indexOps(CollectionName.REPLAYS)).thenReturn(replays);
        when(replays.getIndexInfo()).thenReturn(List.of());

        MongoIndexBootstrapService service = new MongoIndexBootstrapService(tenantMongoAccess, mock(TenantMigrationService.class));
        service.createTenantIndexes(template);

        ArgumentCaptor<IndexDefinition> replayIndexCaptor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(replays, atLeastOnce()).createIndex(replayIndexCaptor.capture());
        assertThat(replayIndexCaptor.getAllValues()).anySatisfy(index -> {
            assertThat(index.getIndexOptions().getString("name")).isEqualTo("idx_replays_targetUuid_createdAt");
            assertThat(index.getIndexKeys()).isEqualTo(new Document("targetUuid", 1).append("createdAt", -1));
        });
        assertThat(replayIndexCaptor.getAllValues()).anySatisfy(index -> {
            assertThat(index.getIndexOptions().getString("name")).isEqualTo("idx_replays_status_createdAt");
            assertThat(index.getIndexKeys()).isEqualTo(new Document("status", 1).append("createdAt", 1).append("_id", 1));
        });
    }

    @Test
    void createTenantIndexesCreatesStorageFilesAndCaseInsensitiveUsernameIndexes() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        IndexOperations replays = mock(IndexOperations.class);
        IndexOperations storageFiles = mock(IndexOperations.class);
        IndexOperations players = mock(IndexOperations.class);
        stubTenantIndexOps(template);
        when(template.indexOps(CollectionName.REPLAYS)).thenReturn(replays);
        when(replays.getIndexInfo()).thenReturn(List.of());
        when(template.indexOps(CollectionName.STORAGE_FILES)).thenReturn(storageFiles);
        when(storageFiles.getIndexInfo()).thenReturn(List.of());
        when(template.indexOps(CollectionName.PLAYERS)).thenReturn(players);
        when(players.getIndexInfo()).thenReturn(List.of());

        MongoIndexBootstrapService service = new MongoIndexBootstrapService(tenantMongoAccess, mock(TenantMigrationService.class));
        service.createTenantIndexes(template);

        ArgumentCaptor<IndexDefinition> storageCaptor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(storageFiles, atLeastOnce()).createIndex(storageCaptor.capture());
        assertThat(storageCaptor.getAllValues()).anySatisfy(index -> {
            assertThat(index.getIndexOptions().getString("name")).isEqualTo("uidx_storage_files_key");
            assertThat(index.getIndexKeys()).isEqualTo(new Document("key", 1));
            assertThat(index.getIndexOptions().getBoolean("unique")).isTrue();
        });
        assertThat(storageCaptor.getAllValues()).anySatisfy(index -> {
            assertThat(index.getIndexOptions().getString("name")).isEqualTo("idx_storage_files_key_createdAt");
            assertThat(index.getIndexKeys()).isEqualTo(new Document("key", 1).append("createdAt", -1));
        });

        ArgumentCaptor<IndexDefinition> playerCaptor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(players, atLeastOnce()).createIndex(playerCaptor.capture());
        assertThat(playerCaptor.getAllValues()).anySatisfy(index -> {
            assertThat(index.getIndexOptions().getString("name")).isEqualTo("idx_players_usernames_username_ci");
            assertThat(index.getIndexKeys()).isEqualTo(new Document("usernames.username", 1));
            assertThat(index.getIndexOptions().get("collation")).isNotNull();
        });
    }

    @Test
    void createTenantIndexesLeavesEquivalentPartialUniquePlayerIndexUntouched() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        IndexOperations players = mock(IndexOperations.class);
        IndexOperations replays = mock(IndexOperations.class);
        stubTenantIndexOps(template);
        when(template.indexOps(CollectionName.REPLAYS)).thenReturn(replays);
        when(replays.getIndexInfo()).thenReturn(List.of());
        when(template.indexOps(CollectionName.PLAYERS)).thenReturn(players);
        when(players.getIndexInfo()).thenReturn(List.of(IndexInfo.indexInfoOf(
            new Document("name", "uidx_players_minecraftUuid")
                .append("key", new Document("minecraftUuid", 1))
                .append("unique", true)
                .append("partialFilterExpression", new Document("minecraftUuid", new Document("$type", 2)))
        )));

        MongoIndexBootstrapService service = new MongoIndexBootstrapService(
            tenantMongoAccess, mock(TenantMigrationService.class));
        service.createTenantIndexes(template);

        verify(players, never()).dropIndex("uidx_players_minecraftUuid");
        ArgumentCaptor<IndexDefinition> created = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(players, atLeastOnce()).createIndex(created.capture());
        assertThat(created.getAllValues())
            .noneMatch(index -> "uidx_players_minecraftUuid".equals(index.getIndexOptions().getString("name")));
    }

    @Test
    void bootstrapExistingTenantsAppliesIndexesToEachConfiguredServer() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate globalTemplate = mock(MongoTemplate.class);
        MongoTemplate firstTemplate = mock(MongoTemplate.class);
        MongoTemplate secondTemplate = mock(MongoTemplate.class);
        IndexOperations firstReplays = mock(IndexOperations.class);
        IndexOperations secondReplays = mock(IndexOperations.class);
        stubTenantIndexOps(firstTemplate);
        stubTenantIndexOps(secondTemplate);
        when(firstTemplate.indexOps(CollectionName.REPLAYS)).thenReturn(firstReplays);
        when(secondTemplate.indexOps(CollectionName.REPLAYS)).thenReturn(secondReplays);
        when(firstReplays.getIndexInfo()).thenReturn(List.of());
        when(secondReplays.getIndexInfo()).thenReturn(List.of());

        BootstrapTarget configured = new BootstrapTarget("alpha", "server_alpha");
        BootstrapTarget alsoConfigured = new BootstrapTarget("beta", "server_beta");
        BootstrapTarget unprovisioned = new BootstrapTarget("gamma", null);
        when(tenantMongoAccess.global()).thenReturn(globalTemplate);
        when(globalTemplate.find(any(Query.class), eq(BootstrapTarget.class), eq(CollectionName.MODL_SERVERS)))
            .thenReturn(List.of(configured, alsoConfigured, unprovisioned));
        when(tenantMongoAccess.forDatabase("server_alpha")).thenReturn(firstTemplate);
        when(tenantMongoAccess.forDatabase("server_beta")).thenReturn(secondTemplate);

        MongoIndexBootstrapService service = new MongoIndexBootstrapService(tenantMongoAccess, mock(TenantMigrationService.class));
        service.bootstrapExistingTenants();

        verify(firstReplays, timeout(5000).atLeastOnce()).createIndex(any());
        verify(secondReplays, timeout(5000).atLeastOnce()).createIndex(any());
        verify(tenantMongoAccess, never()).forDatabase(null);
    }

    @Test
    void bootstrapExistingTenantsContinuesWhenIndividualTenantFails() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate globalTemplate = mock(MongoTemplate.class);
        MongoTemplate goodTemplate = mock(MongoTemplate.class);
        IndexOperations goodReplays = mock(IndexOperations.class);
        stubTenantIndexOps(goodTemplate);
        when(goodTemplate.indexOps(CollectionName.REPLAYS)).thenReturn(goodReplays);
        when(goodReplays.getIndexInfo()).thenReturn(List.of());

        BootstrapTarget broken = new BootstrapTarget("broken", "server_broken");
        BootstrapTarget healthy = new BootstrapTarget("healthy", "server_healthy");
        when(tenantMongoAccess.global()).thenReturn(globalTemplate);
        when(globalTemplate.find(any(Query.class), eq(BootstrapTarget.class), eq(CollectionName.MODL_SERVERS)))
            .thenReturn(List.of(broken, healthy));
        when(tenantMongoAccess.forDatabase("server_broken")).thenThrow(new IllegalStateException("boom"));
        when(tenantMongoAccess.forDatabase("server_healthy")).thenReturn(goodTemplate);

        MongoIndexBootstrapService service = new MongoIndexBootstrapService(tenantMongoAccess, mock(TenantMigrationService.class));
        service.bootstrapExistingTenants();

        verify(goodReplays, timeout(5000).atLeastOnce()).createIndex(any());
    }

    private void stubTenantIndexOps(MongoTemplate template) {
        List<String> collectionNames = List.of(
            CollectionName.SETTINGS,
            CollectionName.PLAYERS,
            CollectionName.STAFF,
            CollectionName.STAFF_ROLES,
            CollectionName.INVITATIONS,
            CollectionName.TICKET_VERIFICATIONS,
            CollectionName.TICKETS,
            CollectionName.STORAGE_FILES,
            CollectionName.KNOWLEDGEBASE_CATEGORIES,
            CollectionName.KNOWLEDGEBASE_ARTICLES,
            CollectionName.WEBAUTHN_CREDENTIALS,
            CollectionName.WEBAUTHN_CHALLENGES,
            CollectionName.HOMEPAGE_CARDS,
            CollectionName.SESSIONS,
            CollectionName.AUTH_CODES,
            CollectionName.SYSTEM_LOGS,
            CollectionName.SECURITY_EVENTS,
            CollectionName.CHAT_LOGS,
            CollectionName.COMMAND_LOGS,
            CollectionName.LOGS,
            CollectionName.MIGRATIONS
        );
        for (String collectionName : collectionNames) {
            IndexOperations indexOperations = mock(IndexOperations.class);
            when(template.indexOps(collectionName)).thenReturn(indexOperations);
            when(indexOperations.getIndexInfo()).thenReturn(List.of());
        }
    }

    private IndexDefinition captureCreatedIndex(IndexOperations indexOperations) {
        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations).createIndex(captor.capture());
        return captor.getValue();
    }
}
