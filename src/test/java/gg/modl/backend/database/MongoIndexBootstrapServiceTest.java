package gg.modl.backend.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

class MongoIndexBootstrapServiceTest {
    @Test
    void initGlobalIndexesSkipsEquivalentLegacyIndexWithDifferentName() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        IndexOperations servers = mock(IndexOperations.class);
        IndexOperations metrics = mock(IndexOperations.class);
        IndexOperations adminUsers = mock(IndexOperations.class);

        when(tenantMongoAccess.global()).thenReturn(template);
        when(template.indexOps(CollectionName.MODL_SERVERS)).thenReturn(servers);
        when(template.indexOps(CollectionName.METRIC_SNAPSHOTS)).thenReturn(metrics);
        when(template.indexOps("admin_users")).thenReturn(adminUsers);

        when(servers.getIndexInfo()).thenReturn(List.of());
        when(metrics.getIndexInfo()).thenReturn(List.of());
        when(adminUsers.getIndexInfo()).thenReturn(List.of(IndexInfo.indexInfoOf(
            new Document("name", "email_1")
                .append("key", new Document("email", 1))
                .append("unique", true)
        )));

        MongoIndexBootstrapService service = new MongoIndexBootstrapService(tenantMongoAccess);
        service.initGlobalIndexes();

        verify(adminUsers, never()).createIndex(any());
        org.mockito.ArgumentCaptor<IndexDefinition> serverIndexCaptor = org.mockito.ArgumentCaptor.forClass(IndexDefinition.class);
        verify(servers, atLeastOnce()).createIndex(serverIndexCaptor.capture());
        assertThat(serverIndexCaptor.getAllValues()).anySatisfy(index -> {
            assertThat(index.getIndexOptions().getString("name")).isEqualTo("idx_servers_registration_cleanup");
            assertThat(index.getIndexKeys()).isEqualTo(new Document("emailVerified", 1)
                .append("provisioningStatus", 1)
                .append("createdAt", 1)
                .append("emailVerificationToken", 1));
        });
        verify(metrics, atLeastOnce()).createIndex(any());
    }

    @Test
    void initGlobalIndexesCreatesAdminIndexWhenLegacyIndexOptionsDiffer() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        MongoTemplate template = mock(MongoTemplate.class);
        IndexOperations servers = mock(IndexOperations.class);
        IndexOperations metrics = mock(IndexOperations.class);
        IndexOperations adminUsers = mock(IndexOperations.class);

        when(tenantMongoAccess.global()).thenReturn(template);
        when(template.indexOps(CollectionName.MODL_SERVERS)).thenReturn(servers);
        when(template.indexOps(CollectionName.METRIC_SNAPSHOTS)).thenReturn(metrics);
        when(template.indexOps("admin_users")).thenReturn(adminUsers);

        when(servers.getIndexInfo()).thenReturn(List.of());
        when(metrics.getIndexInfo()).thenReturn(List.of());
        when(adminUsers.getIndexInfo()).thenReturn(List.of(IndexInfo.indexInfoOf(
            new Document("name", "email_1")
                .append("key", new Document("email", 1))
        )));

        MongoIndexBootstrapService service = new MongoIndexBootstrapService(tenantMongoAccess);
        service.initGlobalIndexes();

        verify(adminUsers).createIndex(any());
        IndexDefinition index = captureCreatedIndex(adminUsers);
        assertThat(index.getIndexKeys()).isEqualTo(new Document("email", 1));
        assertThat(index.getIndexOptions().getString("name")).isEqualTo("uidx_admin_users_email");
        assertThat(index.getIndexOptions().getBoolean("unique")).isTrue();
    }

    private IndexDefinition captureCreatedIndex(IndexOperations indexOperations) {
        org.mockito.ArgumentCaptor<IndexDefinition> captor = org.mockito.ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations).createIndex(captor.capture());
        return captor.getValue();
    }
}
