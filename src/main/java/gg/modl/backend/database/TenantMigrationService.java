package gg.modl.backend.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.fields.SettingsFields;
import gg.modl.backend.database.mongo.fields.StaffRoleFields;
import gg.modl.backend.database.mongo.fields.TicketFields;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.service.DuplicatePlayerMerger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonType;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantMigrationService {
    static final String LOWERCASE_TICKET_UUIDS_MIGRATION_ID = "lowercase-ticket-uuids";
    static final String BACKFILL_STAFF_ROLE_IDS_MIGRATION_ID = "backfill-staff-role-ids";
    static final String DEDUPE_SETTINGS_BY_TYPE_MIGRATION_ID = "dedupe-settings-by-type";
    static final String DEDUPE_PLAYERS_BY_MINECRAFT_UUID_MIGRATION_ID = "dedupe-players-by-minecraft-uuid-v2";
    static final String NORMALIZE_PLAYER_IDS_MIGRATION_ID = "normalize-player-ids";
    private static final Pattern UPPERCASE_HEX_PATTERN = Pattern.compile("[A-F]");
    private static final String ROLE_FIELD = "role";
    private static final String ID_FIELD = "_id";

    private static final Comparator<Document> SETTINGS_RECENCY = Comparator
        .comparing(TenantMigrationService::settingsVersion, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(TenantMigrationService::settingsUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(TenantMigrationService::settingsId, Comparator.nullsFirst(Comparator.naturalOrder()))
        .reversed();

    private final DuplicatePlayerMerger duplicatePlayerMerger;

    void applyMigrationsForTenant(MongoTemplate template) {
        runMigrationOnce(template, LOWERCASE_TICKET_UUIDS_MIGRATION_ID, this::lowercaseTicketUuids);
        runMigrationOnce(template, BACKFILL_STAFF_ROLE_IDS_MIGRATION_ID, this::backfillStaffRoleIds);
        runMigrationOnce(template, DEDUPE_SETTINGS_BY_TYPE_MIGRATION_ID, this::dedupeSettingsByType);
        runMigrationOnce(template, DEDUPE_PLAYERS_BY_MINECRAFT_UUID_MIGRATION_ID, this::dedupePlayersByMinecraftUuid);
        runMigrationOnce(template, NORMALIZE_PLAYER_IDS_MIGRATION_ID, this::normalizePlayerIds);
    }

    private void runMigrationOnce(MongoTemplate template, String migrationId, MigrationStep step) {
        if (isMigrationApplied(template, migrationId)) {
            return;
        }
        step.run(template);
        markMigrationApplied(template, migrationId);
    }

    private boolean isMigrationApplied(MongoTemplate template, String migrationId) {
        Document marker = template.getCollection(CollectionName.TENANT_MIGRATIONS)
            .find(Filters.eq("_id", migrationId))
            .first();
        return marker != null;
    }

    private void markMigrationApplied(MongoTemplate template, String migrationId) {
        template.getCollection(CollectionName.TENANT_MIGRATIONS).updateOne(
            Filters.eq("_id", migrationId),
            Updates.set("appliedAt", new Date()),
            new UpdateOptions().upsert(true)
        );
    }

    private void lowercaseTicketUuids(MongoTemplate template) {
        MongoCollection<Document> tickets = template.getCollection(CollectionName.TICKETS);

        Bson filter = Filters.or(
            Filters.regex(TicketFields.CREATOR_UUID, UPPERCASE_HEX_PATTERN),
            Filters.regex(TicketFields.REPORTED_PLAYER_UUID, UPPERCASE_HEX_PATTERN)
        );

        List<Bson> pipeline = List.of(new Document("$set", new Document()
            .append(TicketFields.CREATOR_UUID, conditionalLowercase(TicketFields.CREATOR_UUID))
            .append(TicketFields.REPORTED_PLAYER_UUID, conditionalLowercase(TicketFields.REPORTED_PLAYER_UUID))
        ));

        UpdateResult result = tickets.updateMany(filter, pipeline);
        log.info("Lowercased ticket UUIDs in database={} matched={} modified={}",
            template.getDb().getName(), result.getMatchedCount(), result.getModifiedCount());
    }

    // Staff and invitations historically stored the role display name; this rewrites those references to the
    // immutable StaffRole id so a later role rename can no longer orphan them. Idempotent and safe to re-run:
    // a value that is already a role id is left alone, and names that collide with a role id are skipped.
    private void backfillStaffRoleIds(MongoTemplate template) {
        List<Document> roles = template.getCollection(CollectionName.STAFF_ROLES)
            .find()
            .into(new ArrayList<>());

        Set<String> roleIds = new HashSet<>();
        for (Document role : roles) {
            String id = stringifyId(role.get(ID_FIELD));
            if (id != null) {
                roleIds.add(id);
            }
        }

        MongoCollection<Document> staff = template.getCollection(CollectionName.STAFF);
        MongoCollection<Document> invitations = template.getCollection(CollectionName.INVITATIONS);

        long staffUpdated = 0;
        long invitationsUpdated = 0;
        for (Document role : roles) {
            String id = stringifyId(role.get(ID_FIELD));
            String name = role.getString(StaffRoleFields.NAME);
            if (id == null || name == null || name.equals(id)) {
                continue;
            }
            if (roleIds.contains(name)) {
                log.warn("Skipping role-id backfill for role name '{}' in database={} because it collides with an existing role id",
                    name, template.getDb().getName());
                continue;
            }
            staffUpdated += staff.updateMany(Filters.eq(ROLE_FIELD, name), Updates.set(ROLE_FIELD, id)).getModifiedCount();
            invitationsUpdated += invitations.updateMany(Filters.eq(ROLE_FIELD, name), Updates.set(ROLE_FIELD, id)).getModifiedCount();
        }

        long staffOrphans = staff.countDocuments(Filters.nin(ROLE_FIELD, roleIds));
        long invitationOrphans = invitations.countDocuments(Filters.nin(ROLE_FIELD, roleIds));
        if (staffOrphans > 0 || invitationOrphans > 0) {
            log.warn("Role-id backfill left unresolved role references in database={} staffOrphans={} invitationOrphans={}",
                template.getDb().getName(), staffOrphans, invitationOrphans);
        }
        log.info("Backfilled staff role ids in database={} staffUpdated={} invitationsUpdated={}",
            template.getDb().getName(), staffUpdated, invitationsUpdated);
    }

    private void dedupeSettingsByType(MongoTemplate template) {
        MongoCollection<Document> settings = template.getCollection(CollectionName.SETTINGS);
        long removedUntyped = settings.deleteMany(Filters.eq(SettingsFields.TYPE, null)).getDeletedCount();

        Map<String, List<Document>> byType = new LinkedHashMap<>();
        List<Document> typedSettings = settings.find(Filters.ne(SettingsFields.TYPE, null)).into(new ArrayList<>());
        for (Document document : typedSettings) {
            byType.computeIfAbsent(document.getString(SettingsFields.TYPE), key -> new ArrayList<>()).add(document);
        }

        List<Object> removableIds = new ArrayList<>();
        for (List<Document> group : byType.values()) {
            if (group.size() < 2) {
                continue;
            }
            group.sort(SETTINGS_RECENCY);
            for (Document stale : group.subList(1, group.size())) {
                removableIds.add(stale.get(ID_FIELD));
            }
        }

        long removedDuplicates = removeByIds(template, CollectionName.SETTINGS, removableIds);
        log.info("Deduped settings by type in database={} removedUntyped={} removedDuplicates={}",
            template.getDb().getName(), removedUntyped, removedDuplicates);
    }

    private void dedupePlayersByMinecraftUuid(MongoTemplate template) {
        List<String> duplicateUuids = findDuplicateMinecraftUuids(template);
        if (duplicateUuids.isEmpty()) {
            return;
        }

        MongoCollection<Document> players = template.getCollection(CollectionName.PLAYERS);
        MongoConverter converter = template.getConverter();

        long mergedGroups = 0;
        List<Object> removableIds = new ArrayList<>();
        for (String minecraftUuid : duplicateUuids) {
            List<Document> rawGroup = players
                .find(Filters.eq(PlayerFields.MINECRAFT_UUID, minecraftUuid))
                .into(new ArrayList<>());
            if (rawGroup.size() < 2) {
                continue;
            }

            Map<Player, Object> storedIdByPlayer = new IdentityHashMap<>();
            List<Player> group = new ArrayList<>(rawGroup.size());
            for (Document rawPlayer : rawGroup) {
                Player player = converter.read(Player.class, rawPlayer);
                group.add(player);
                storedIdByPlayer.put(player, rawPlayer.get(PlayerFields.ID));
            }

            Player primary = duplicatePlayerMerger.merge(group);
            persistMergedPlayer(players, converter, primary, storedIdByPlayer.get(primary));

            for (Player player : group) {
                if (player != primary) {
                    removableIds.add(storedIdByPlayer.get(player));
                }
            }
            mergedGroups++;
        }

        long removedDocuments = removeByIds(template, CollectionName.PLAYERS, removableIds);
        log.info("Deduped players by minecraftUuid in database={} mergedGroups={} removedDocuments={}",
            template.getDb().getName(), mergedGroups, removedDocuments);
    }

    private void persistMergedPlayer(MongoCollection<Document> players, MongoConverter converter,
                                     Player primary, Object storedId) {
        Document merged = new Document();
        converter.write(primary, merged);
        players.updateOne(Filters.eq(PlayerFields.ID, storedId), Updates.combine(
            Updates.set(PlayerFields.USERNAMES, merged.get(PlayerFields.USERNAMES)),
            Updates.set(PlayerFields.IP_ADDRESSES, merged.get(PlayerFields.IP_ADDRESSES)),
            Updates.set(PlayerFields.NOTES, merged.get(PlayerFields.NOTES)),
            Updates.set(PlayerFields.PUNISHMENTS, merged.get(PlayerFields.PUNISHMENTS)),
            Updates.set(PlayerFields.DATA, merged.get(PlayerFields.DATA))
        ));
    }

    private void normalizePlayerIds(MongoTemplate template) {
        MongoCollection<Document> players = template.getCollection(CollectionName.PLAYERS);
        List<Document> legacyPlayers = players
            .find(Filters.type(PlayerFields.ID, BsonType.OBJECT_ID))
            .into(new ArrayList<>());

        List<WriteModel<Document>> operations = new ArrayList<>(legacyPlayers.size() * 2);
        for (Document legacyPlayer : legacyPlayers) {
            Object legacyId = legacyPlayer.get(PlayerFields.ID);
            Document reKeyedPlayer = new Document(legacyPlayer);
            reKeyedPlayer.put(PlayerFields.ID, legacyId.toString());
            operations.add(new DeleteOneModel<>(Filters.eq(PlayerFields.ID, legacyId)));
            operations.add(new InsertOneModel<>(reKeyedPlayer));
        }

        if (!operations.isEmpty()) {
            players.bulkWrite(operations);
        }
        log.info("Normalized player ids in database={} reKeyed={}",
            template.getDb().getName(), legacyPlayers.size());
    }

    private List<String> findDuplicateMinecraftUuids(MongoTemplate template) {
        List<Bson> pipeline = List.of(
            new Document("$match", new Document(PlayerFields.MINECRAFT_UUID, new Document("$type", "string"))),
            new Document("$group", new Document(ID_FIELD, "$" + PlayerFields.MINECRAFT_UUID)
                .append("count", new Document("$sum", 1))),
            new Document("$match", new Document("count", new Document("$gt", 1)))
        );

        List<Document> groups = template.getCollection(CollectionName.PLAYERS)
            .aggregate(pipeline)
            .allowDiskUse(true)
            .into(new ArrayList<>());
        List<String> minecraftUuids = new ArrayList<>();
        for (Document group : groups) {
            Object minecraftUuid = group.get(ID_FIELD);
            if (minecraftUuid != null) {
                minecraftUuids.add(minecraftUuid.toString());
            }
        }
        return minecraftUuids;
    }

    private long removeByIds(MongoTemplate template, String collectionName, List<Object> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return template.getCollection(collectionName).deleteMany(Filters.in(ID_FIELD, ids)).getDeletedCount();
    }

    private static Long settingsVersion(Document document) {
        return document.get(SettingsFields.VERSION) instanceof Number number ? number.longValue() : null;
    }

    private static Date settingsUpdatedAt(Document document) {
        return document.get(SettingsFields.UPDATED_AT) instanceof Date date ? date : null;
    }

    private static String settingsId(Document document) {
        Object rawId = document.get(ID_FIELD);
        return rawId == null ? null : rawId.toString();
    }

    private static String stringifyId(Object rawId) {
        if (rawId == null) {
            return null;
        }
        if (rawId instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        return rawId.toString();
    }

    private static Document conditionalLowercase(String field) {
        String fieldRef = "$" + field;
        Document isNonEmptyString = new Document("$and", List.of(
            new Document("$eq", List.of(new Document("$type", fieldRef), "string")),
            new Document("$ne", List.of(fieldRef, ""))
        ));
        return new Document("$cond", List.of(
            isNonEmptyString,
            new Document("$toLower", fieldRef),
            fieldRef
        ));
    }

    @FunctionalInterface
    private interface MigrationStep {
        void run(MongoTemplate template);
    }
}
