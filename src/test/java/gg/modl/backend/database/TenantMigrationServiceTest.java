package gg.modl.backend.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.player.service.DuplicatePlayerMerger;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class TenantMigrationServiceTest {
    @Test
    void appliesLowercaseUuidMigrationWhenMarkerMissing() {
        MongoTemplate template = mock(MongoTemplate.class);
        MongoCollection<Document> migrations = mockMigrationsCollection(template, null);
        MongoCollection<Document> tickets = mockTicketsCollection(template, 3L, 2L);
        mockRoleCollections(template, List.of());
        mockEmptyDedupeCollections(template);

        TenantMigrationService service = new TenantMigrationService(mock(DuplicatePlayerMerger.class));
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

        ArgumentCaptor<Bson> markerFilter = ArgumentCaptor.forClass(Bson.class);
        verify(migrations, times(5)).updateOne(markerFilter.capture(), any(Bson.class), any(UpdateOptions.class));
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

        TenantMigrationService service = new TenantMigrationService(mock(DuplicatePlayerMerger.class));
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
        mockEmptyDedupeCollections(template);

        TenantMigrationService service = new TenantMigrationService(mock(DuplicatePlayerMerger.class));
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
    void backfillRewritesRoleNamesToHexIdsWhenIdStoredAsObjectId() {
        MongoTemplate template = mock(MongoTemplate.class);
        mockMigrationsCollection(template, null);
        mockTicketsCollection(template, 0L, 0L);
        ObjectId helperId = new ObjectId("507f1f77bcf86cd799439011");
        RoleCollections roleCollections = mockRoleCollections(template, List.of(
            new Document("_id", helperId).append("name", "Helper")
        ));
        mockEmptyDedupeCollections(template);

        TenantMigrationService service = new TenantMigrationService(mock(DuplicatePlayerMerger.class));
        service.applyMigrationsForTenant(template);

        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> updateCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(roleCollections.staff()).updateMany(filterCaptor.capture(), updateCaptor.capture());
        assertThat(toJson(filterCaptor.getValue())).contains("\"role\": \"Helper\"");
        assertThat(toJson(updateCaptor.getValue())).contains("\"role\": \"" + helperId.toHexString() + "\"");
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
        mockEmptyDedupeCollections(template);

        TenantMigrationService service = new TenantMigrationService(mock(DuplicatePlayerMerger.class));
        service.applyMigrationsForTenant(template);

        // "Admin" -> "admin" is safe; the colliding "admin" name is skipped so already-migrated docs are never clobbered.
        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(roleCollections.staff(), times(1)).updateMany(filterCaptor.capture(), any(Bson.class));
        assertThat(toJson(filterCaptor.getValue())).contains("\"role\": \"Admin\"");
    }

    @Test
    void dedupeSettingsDropsUntypedAndCollapsesDuplicateTypes() {
        MongoTemplate template = mock(MongoTemplate.class);
        mockMigrationsCollection(template, null);
        mockTicketsCollection(template, 0L, 0L);
        mockRoleCollections(template, List.of());
        mockEmptyPlayersDedupe(template);

        @SuppressWarnings("unchecked")
        MongoCollection<Document> settings = mock(MongoCollection.class);
        @SuppressWarnings("unchecked")
        FindIterable<Document> typedSettings = mock(FindIterable.class);
        ObjectId keepId = new ObjectId("507f1f77bcf86cd799439011");
        ObjectId staleId = new ObjectId("507f1f77bcf86cd799439012");
        Document keep = new Document("_id", keepId).append("type", "general").append("version", 5L);
        Document stale = new Document("_id", staleId).append("type", "general").append("version", 2L);
        when(template.getCollection(CollectionName.SETTINGS)).thenReturn(settings);
        when(settings.deleteMany(any(Bson.class))).thenReturn(DeleteResult.acknowledged(1));
        when(settings.find(any(Bson.class))).thenReturn(typedSettings);
        when(typedSettings.into(any())).thenAnswer(invocation -> {
            List<Document> sink = invocation.getArgument(0);
            sink.add(keep);
            sink.add(stale);
            return sink;
        });

        new TenantMigrationService(mock(DuplicatePlayerMerger.class)).applyMigrationsForTenant(template);

        ArgumentCaptor<Bson> deleteCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(settings, times(2)).deleteMany(deleteCaptor.capture());
        List<String> deletes = deleteCaptor.getAllValues().stream().map(TenantMigrationServiceTest::toJson).toList();
        assertThat(deletes).anyMatch(json -> json.contains("\"type\": null"));
        assertThat(deletes).anyMatch(json -> json.contains(staleId.toHexString()) && !json.contains(keepId.toHexString()));
    }

    @Test
    void dedupePlayersMergesDuplicatesAndDeletesLosersByStoredId() {
        MongoTemplate template = mock(MongoTemplate.class);
        mockMigrationsCollection(template, null);
        mockTicketsCollection(template, 0L, 0L);
        mockRoleCollections(template, List.of());
        mockEmptySettingsDedupe(template);

        String minecraftUuid = "966eea38-a14e-3e73-a517-0438b82d88e8";
        ObjectId loserStoredId = new ObjectId("507f1f77bcf86cd799439011");
        @SuppressWarnings("unchecked")
        MongoCollection<Document> players = mock(MongoCollection.class);
        @SuppressWarnings("unchecked")
        AggregateIterable<Document> aggregate = mock(AggregateIterable.class);
        @SuppressWarnings("unchecked")
        FindIterable<Document> groupFind = mock(FindIterable.class);
        when(template.getCollection(CollectionName.PLAYERS)).thenReturn(players);
        when(template.getConverter()).thenReturn(playerConverter());
        when(players.aggregate(anyList())).thenReturn(aggregate);
        when(aggregate.allowDiskUse(anyBoolean())).thenReturn(aggregate);
        when(aggregate.into(any())).thenAnswer(invocation -> {
            List<Document> sink = invocation.getArgument(0);
            sink.add(new Document("_id", minecraftUuid).append("count", 2));
            return sink;
        });
        @SuppressWarnings("unchecked")
        FindIterable<Document> normalizeFind = mock(FindIterable.class);
        when(players.find(any(Bson.class))).thenReturn(groupFind, normalizeFind);
        when(normalizeFind.into(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groupFind.into(any())).thenAnswer(invocation -> {
            List<Document> sink = invocation.getArgument(0);
            sink.add(new Document("_id", "keep").append("minecraftUuid", minecraftUuid)
                .append("punishments", List.of(new Document("id", "p1"), new Document("id", "p2"))));
            sink.add(new Document("_id", loserStoredId).append("minecraftUuid", minecraftUuid)
                .append("punishments", List.of(new Document("id", "p3"))));
            return sink;
        });
        when(players.updateOne(any(Bson.class), any(Bson.class))).thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(players.deleteMany(any(Bson.class))).thenReturn(DeleteResult.acknowledged(1));

        new TenantMigrationService(new DuplicatePlayerMerger()).applyMigrationsForTenant(template);

        ArgumentCaptor<Bson> updateFilterCaptor = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> updateCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(players).updateOne(updateFilterCaptor.capture(), updateCaptor.capture());
        assertThat(toJson(updateFilterCaptor.getValue())).contains("\"_id\": \"keep\"");
        assertThat(toJson(updateCaptor.getValue())).contains("p1").contains("p2").contains("p3");

        ArgumentCaptor<Bson> deleteCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(players).deleteMany(deleteCaptor.capture());
        String deleteJson = toJson(deleteCaptor.getValue());
        assertThat(deleteJson).contains("$oid").contains(loserStoredId.toHexString());
        assertThat(deleteJson).doesNotContain("keep");
    }

    @Test
    void dedupePlayersPersistsMergedPrimaryByStoredObjectId() {
        MongoTemplate template = mock(MongoTemplate.class);
        mockMigrationsCollection(template, null);
        mockTicketsCollection(template, 0L, 0L);
        mockRoleCollections(template, List.of());
        mockEmptySettingsDedupe(template);

        String minecraftUuid = "966eea38-a14e-3e73-a517-0438b82d88e8";
        ObjectId primaryStoredId = new ObjectId("507f191e810c19729de860ea");
        @SuppressWarnings("unchecked")
        MongoCollection<Document> players = mock(MongoCollection.class);
        @SuppressWarnings("unchecked")
        AggregateIterable<Document> aggregate = mock(AggregateIterable.class);
        @SuppressWarnings("unchecked")
        FindIterable<Document> groupFind = mock(FindIterable.class);
        when(template.getCollection(CollectionName.PLAYERS)).thenReturn(players);
        when(template.getConverter()).thenReturn(playerConverter());
        when(players.aggregate(anyList())).thenReturn(aggregate);
        when(aggregate.allowDiskUse(anyBoolean())).thenReturn(aggregate);
        when(aggregate.into(any())).thenAnswer(invocation -> {
            List<Document> sink = invocation.getArgument(0);
            sink.add(new Document("_id", minecraftUuid).append("count", 2));
            return sink;
        });
        @SuppressWarnings("unchecked")
        FindIterable<Document> normalizeFind = mock(FindIterable.class);
        when(players.find(any(Bson.class))).thenReturn(groupFind, normalizeFind);
        when(normalizeFind.into(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groupFind.into(any())).thenAnswer(invocation -> {
            List<Document> sink = invocation.getArgument(0);
            sink.add(new Document("_id", primaryStoredId).append("minecraftUuid", minecraftUuid)
                .append("punishments", List.of(new Document("id", "p1"), new Document("id", "p2"))));
            sink.add(new Document("_id", "loser").append("minecraftUuid", minecraftUuid)
                .append("punishments", List.of(new Document("id", "p3"))));
            return sink;
        });
        when(players.updateOne(any(Bson.class), any(Bson.class))).thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(players.deleteMany(any(Bson.class))).thenReturn(DeleteResult.acknowledged(1));

        new TenantMigrationService(new DuplicatePlayerMerger()).applyMigrationsForTenant(template);

        ArgumentCaptor<Bson> updateFilterCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(players).updateOne(updateFilterCaptor.capture(), any(Bson.class));
        assertThat(toJson(updateFilterCaptor.getValue())).contains("$oid").contains(primaryStoredId.toHexString());

        ArgumentCaptor<Bson> deleteCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(players).deleteMany(deleteCaptor.capture());
        assertThat(toJson(deleteCaptor.getValue())).contains("loser").doesNotContain("$oid");
    }

    @Test
    void normalizePlayerIdsReKeysLegacyObjectIdDocumentsToStrings() {
        MongoTemplate template = mock(MongoTemplate.class);
        mockMigrationsCollection(template, null);
        mockTicketsCollection(template, 0L, 0L);
        mockRoleCollections(template, List.of());
        mockEmptySettingsDedupe(template);

        ObjectId legacyId = new ObjectId("507f1f77bcf86cd799439011");
        String uuid = "966eea38-a14e-3e73-a517-0438b82d88e8";
        @SuppressWarnings("unchecked")
        MongoCollection<Document> players = mock(MongoCollection.class);
        @SuppressWarnings("unchecked")
        AggregateIterable<Document> aggregate = mock(AggregateIterable.class);
        @SuppressWarnings("unchecked")
        FindIterable<Document> legacyFind = mock(FindIterable.class);
        when(template.getCollection(CollectionName.PLAYERS)).thenReturn(players);
        when(players.aggregate(anyList())).thenReturn(aggregate);
        when(aggregate.allowDiskUse(anyBoolean())).thenReturn(aggregate);
        when(aggregate.into(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(players.find(any(Bson.class))).thenReturn(legacyFind);
        when(legacyFind.into(any())).thenAnswer(invocation -> {
            List<Document> sink = invocation.getArgument(0);
            sink.add(new Document("_id", legacyId).append("minecraftUuid", uuid)
                .append("usernames", List.of(new Document("username", "Legacy"))));
            return sink;
        });

        new TenantMigrationService(mock(DuplicatePlayerMerger.class)).applyMigrationsForTenant(template);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WriteModel<Document>>> opsCaptor = ArgumentCaptor.forClass(List.class);
        verify(players).bulkWrite(opsCaptor.capture());
        List<WriteModel<Document>> operations = opsCaptor.getValue();
        assertThat(operations).hasSize(2);
        assertThat(operations.get(0)).isInstanceOf(DeleteOneModel.class);
        assertThat(operations.get(1)).isInstanceOf(InsertOneModel.class);

        DeleteOneModel<Document> delete = (DeleteOneModel<Document>) operations.get(0);
        assertThat(toJson(delete.getFilter())).contains("$oid").contains(legacyId.toHexString());

        InsertOneModel<Document> insert = (InsertOneModel<Document>) operations.get(1);
        Document inserted = insert.getDocument();
        assertThat(inserted.get("_id")).isInstanceOf(String.class).isEqualTo(legacyId.toHexString());
        assertThat(inserted.getString("minecraftUuid")).isEqualTo(uuid);
    }

    private MappingMongoConverter playerConverter() {
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setAutoIndexCreation(false);
        mappingContext.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        converter.afterPropertiesSet();
        return converter;
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

    private void mockEmptyDedupeCollections(MongoTemplate template) {
        mockEmptySettingsDedupe(template);
        mockEmptyPlayersDedupe(template);
    }

    private void mockEmptySettingsDedupe(MongoTemplate template) {
        @SuppressWarnings("unchecked")
        MongoCollection<Document> settings = mock(MongoCollection.class);
        @SuppressWarnings("unchecked")
        FindIterable<Document> settingsFind = mock(FindIterable.class);
        when(template.getCollection(CollectionName.SETTINGS)).thenReturn(settings);
        when(settings.deleteMany(any(Bson.class))).thenReturn(DeleteResult.acknowledged(0));
        when(settings.find(any(Bson.class))).thenReturn(settingsFind);
        when(settingsFind.into(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void mockEmptyPlayersDedupe(MongoTemplate template) {
        @SuppressWarnings("unchecked")
        MongoCollection<Document> players = mock(MongoCollection.class);
        @SuppressWarnings("unchecked")
        AggregateIterable<Document> playersAggregate = mock(AggregateIterable.class);
        @SuppressWarnings("unchecked")
        FindIterable<Document> playersFind = mock(FindIterable.class);
        when(template.getCollection(CollectionName.PLAYERS)).thenReturn(players);
        when(players.aggregate(anyList())).thenReturn(playersAggregate);
        when(playersAggregate.allowDiskUse(anyBoolean())).thenReturn(playersAggregate);
        when(playersAggregate.into(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(players.find(any(Bson.class))).thenReturn(playersFind);
        when(playersFind.into(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
