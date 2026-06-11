package gg.modl.backend.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

class TenantMigrationServiceTest {
    @Test
    void appliesLowercaseUuidMigrationWhenMarkerMissing() {
        MongoTemplate template = mock(MongoTemplate.class);
        MongoCollection<Document> migrations = mockMigrationsCollection(template, null);
        MongoCollection<Document> tickets = mockTicketsCollection(template, 3L, 2L);
        mockRoleCollections(template, List.of());

        TenantMigrationService service = new TenantMigrationService(
            mock(TenantMongoAccess.class), mock(ServerMongoRepository.class));
        service.applyMigrationsForTenant(template);

        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Bson>> pipelineCaptor = ArgumentCaptor.forClass(List.class);
        verify(tickets).updateMany(filterCaptor.capture(), pipelineCaptor.capture());

        String filterJson = toJson(filterCaptor.getValue());
        assertThat(filterJson).contains("creatorUuid");
        assertThat(filterJson).contains("reportedPlayerUuid");
        assertThat(filterJson).contains("$or");

        List<Bson> pipeline = pipelineCaptor.getValue();
        assertThat(pipeline).hasSize(1);
        String pipelineJson = toJson(pipeline.get(0));
        assertThat(pipelineJson).contains("$set");
        assertThat(pipelineJson).contains("$toLower");
        assertThat(pipelineJson).contains("$creatorUuid");
        assertThat(pipelineJson).contains("$reportedPlayerUuid");

        // Both migrations run when their markers are missing, so each writes its own marker.
        ArgumentCaptor<Bson> markerFilter = ArgumentCaptor.forClass(Bson.class);
        verify(migrations, times(2)).updateOne(markerFilter.capture(), any(Bson.class), any(UpdateOptions.class));
        List<String> markerJsons = markerFilter.getAllValues().stream().map(TenantMigrationServiceTest::toJson).toList();
        assertThat(markerJsons).anyMatch(json -> json.contains(TenantMigrationService.LOWERCASE_TICKET_UUIDS_MIGRATION_ID));
        assertThat(markerJsons).anyMatch(json -> json.contains(TenantMigrationService.BACKFILL_STAFF_ROLE_IDS_MIGRATION_ID));
    }

    @Test
    void skipsMigrationWhenMarkerAlreadyExists() {
        MongoTemplate template = mock(MongoTemplate.class);
        Document existingMarker = new Document("_id", TenantMigrationService.LOWERCASE_TICKET_UUIDS_MIGRATION_ID);
        MongoCollection<Document> migrations = mockMigrationsCollection(template, existingMarker);
        MongoCollection<Document> tickets = mockTicketsCollection(template, 0L, 0L);

        TenantMigrationService service = new TenantMigrationService(
            mock(TenantMongoAccess.class), mock(ServerMongoRepository.class));
        service.applyMigrationsForTenant(template);

        verify(tickets, never()).updateMany(any(Bson.class), anyList());
        verify(migrations, never()).updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class));
    }

    @Test
    void backfillRewritesRoleNamesToRoleIds() {
        MongoTemplate template = mock(MongoTemplate.class);
        mockMigrationsCollection(template, null);
        mockTicketsCollection(template, 0L, 0L);
        RoleCollections roleCollections = mockRoleCollections(template, List.of(
            new Document("_id", "helper").append("name", "Helper"),
            new Document("_id", "admin").append("name", "Admin")
        ));

        TenantMigrationService service = new TenantMigrationService(
            mock(TenantMongoAccess.class), mock(ServerMongoRepository.class));
        service.applyMigrationsForTenant(template);

        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> updateCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(roleCollections.staff(), times(2)).updateMany(filterCaptor.capture(), updateCaptor.capture());
        List<String> filters = filterCaptor.getAllValues().stream().map(TenantMigrationServiceTest::toJson).toList();
        List<String> updates = updateCaptor.getAllValues().stream().map(TenantMigrationServiceTest::toJson).toList();

        // Each role's old name is matched and rewritten to its id, on both staff and invitations.
        assertThat(filters).anyMatch(json -> json.contains("\"role\": \"Helper\""));
        assertThat(updates).anyMatch(json -> json.contains("\"role\": \"helper\""));
        assertThat(filters).anyMatch(json -> json.contains("\"role\": \"Admin\""));
        assertThat(updates).anyMatch(json -> json.contains("\"role\": \"admin\""));
        verify(roleCollections.invitations(), times(2)).updateMany(any(Bson.class), any(Bson.class));
    }

    @Test
    void backfillSkipsRoleNamesThatCollideWithAnExistingRoleId() {
        MongoTemplate template = mock(MongoTemplate.class);
        mockMigrationsCollection(template, null);
        mockTicketsCollection(template, 0L, 0L);
        // A custom role literally named "admin" collides with the default role whose id is "admin".
        RoleCollections roleCollections = mockRoleCollections(template, List.of(
            new Document("_id", "admin").append("name", "Admin"),
            new Document("_id", "custom-1").append("name", "admin")
        ));

        TenantMigrationService service = new TenantMigrationService(
            mock(TenantMongoAccess.class), mock(ServerMongoRepository.class));
        service.applyMigrationsForTenant(template);

        // "Admin" -> "admin" is safe; the colliding "admin" name is skipped so already-migrated docs are never clobbered.
        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(roleCollections.staff(), times(1)).updateMany(filterCaptor.capture(), any(Bson.class));
        assertThat(toJson(filterCaptor.getValue())).contains("\"role\": \"Admin\"");
    }

    private MongoCollection<Document> mockMigrationsCollection(MongoTemplate template, Document existingMarker) {
        @SuppressWarnings("unchecked")
        MongoCollection<Document> migrations = mock(MongoCollection.class);
        @SuppressWarnings("unchecked")
        FindIterable<Document> findIterable = mock(FindIterable.class);
        when(template.getCollection(CollectionName.TENANT_MIGRATIONS)).thenReturn(migrations);
        when(migrations.find(any(Bson.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(existingMarker);
        when(migrations.updateOne(any(Bson.class), any(Bson.class), any(UpdateOptions.class)))
            .thenReturn(UpdateResult.acknowledged(0, 1L, null));
        return migrations;
    }

    private MongoCollection<Document> mockTicketsCollection(MongoTemplate template, long matched, long modified) {
        @SuppressWarnings("unchecked")
        MongoCollection<Document> tickets = mock(MongoCollection.class);
        com.mongodb.client.MongoDatabase database = mock(com.mongodb.client.MongoDatabase.class);
        when(template.getCollection(CollectionName.TICKETS)).thenReturn(tickets);
        when(template.getDb()).thenReturn(database);
        when(database.getName()).thenReturn("tenant_db");
        when(tickets.updateMany(any(Bson.class), anyList()))
            .thenReturn(UpdateResult.acknowledged(matched, modified, null));
        return tickets;
    }

    private record RoleCollections(MongoCollection<Document> staff, MongoCollection<Document> invitations) {}

    private RoleCollections mockRoleCollections(MongoTemplate template, List<Document> roleDocs) {
        @SuppressWarnings("unchecked")
        MongoCollection<Document> roles = mock(MongoCollection.class);
        @SuppressWarnings("unchecked")
        FindIterable<Document> rolesFind = mock(FindIterable.class);
        when(template.getCollection(CollectionName.STAFF_ROLES)).thenReturn(roles);
        when(roles.find()).thenReturn(rolesFind);
        when(rolesFind.into(any())).thenAnswer(invocation -> {
            ArrayList<Document> sink = invocation.getArgument(0);
            sink.addAll(roleDocs);
            return sink;
        });

        @SuppressWarnings("unchecked")
        MongoCollection<Document> staff = mock(MongoCollection.class);
        @SuppressWarnings("unchecked")
        MongoCollection<Document> invitations = mock(MongoCollection.class);
        when(template.getCollection(CollectionName.STAFF)).thenReturn(staff);
        when(template.getCollection(CollectionName.INVITATIONS)).thenReturn(invitations);
        when(staff.updateMany(any(Bson.class), any(Bson.class))).thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(invitations.updateMany(any(Bson.class), any(Bson.class))).thenReturn(UpdateResult.acknowledged(0L, 0L, null));
        return new RoleCollections(staff, invitations);
    }

    private static String toJson(Bson bson) {
        return bson.toBsonDocument(Document.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson();
    }
}
