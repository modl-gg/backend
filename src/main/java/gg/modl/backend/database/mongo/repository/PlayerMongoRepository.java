package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.fields.PunishmentFields;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.server.data.Server;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerMongoRepository extends AbstractServerMongoRepository<Player> {
    public PlayerMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(Player.class, CollectionName.PLAYERS, tenantMongoAccess);
    }

    public Optional<Player> findByMinecraftUuid(Server server, UUID minecraftUuid) {
        return findByMinecraftUuid(server, minecraftUuid.toString());
    }

    public Optional<Player> findByMinecraftUuid(Server server, String minecraftUuid) {
        return findOne(server, Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).is(minecraftUuid)));
    }

    public Optional<Player> findByMinecraftUuid(String databaseName, String minecraftUuid) {
        return findOne(databaseName, Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).is(minecraftUuid)));
    }

    public Optional<Player> findByPunishmentId(Server server, String punishmentId) {
        return findOne(server, Query.query(MongoQueries.where(PlayerFields.PUNISHMENT_ID).is(punishmentId)));
    }

    public Optional<Player> findByUsernameIgnoreCase(Server server, String username) {
        String escapedUsername = Pattern.quote(username.trim());
        Query query = Query.query(MongoQueries.where(PlayerFields.USERNAME).regex("^" + escapedUsername + "$", "i"));
        return findOne(server, query);
    }

    public List<Player> searchByUsernamePattern(Server server, String searchTerm, int limit) {
        Pattern pattern = Pattern.compile(Pattern.quote(searchTerm), Pattern.CASE_INSENSITIVE);
        Query query = Query.query(MongoQueries.where(PlayerFields.USERNAME).regex(pattern));
        query.limit(limit);
        return find(server, query);
    }

    public List<Player> findOnlinePlayers(Server server, int limit) {
        Query query = Query.query(MongoQueries.where(PlayerFields.DATA_IS_ONLINE).is(true));
        query.limit(limit);
        return find(server, query);
    }

    public List<Player> findWithPunishmentsIssuedAfter(Server server, Date cutoff) {
        return find(server, Query.query(MongoQueries.where(PlayerFields.PUNISHMENT_ISSUED).gte(cutoff)));
    }

    public List<Player> findWithPunishmentsIssuedAfter(Server server, Date cutoff, int limit) {
        Query query = Query.query(MongoQueries.where(PlayerFields.PUNISHMENTS).elemMatch(
            Criteria.where("issued").gte(cutoff)
        ));
        query.limit(limit);
        return find(server, query);
    }

    public List<Player> findPlayersWithPunishments(Server server, int limit) {
        Query query = Query.query(MongoQueries.where(PlayerFields.PUNISHMENTS).exists(true));
        query.limit(limit);
        return find(server, query);
    }

    public List<Player> findByMinecraftUuids(Server server, Collection<String> minecraftUuids) {
        if (minecraftUuids == null || minecraftUuids.isEmpty()) {
            return List.of();
        }

        Query query = Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).in(minecraftUuids));
        return find(server, query);
    }

    public List<Player> findByIpAddresses(Server server, Collection<String> ipAddresses) {
        if (ipAddresses == null || ipAddresses.isEmpty()) {
            return List.of();
        }

        Query query = Query.query(MongoQueries.where(PlayerFields.IP_ADDRESS).in(ipAddresses));
        return find(server, query);
    }

    public List<Player> findByIpAddressesExcludingUuid(Server server, Collection<String> ipAddresses, String excludedUuid, int limit) {
        if (ipAddresses == null || ipAddresses.isEmpty()) {
            return List.of();
        }

        Query query = Query.query(new Criteria().andOperator(
            MongoQueries.where(PlayerFields.IP_ADDRESS).in(ipAddresses),
            MongoQueries.where(PlayerFields.MINECRAFT_UUID).ne(excludedUuid)
        ));
        query.limit(limit);
        return find(server, query);
    }

    public List<Player> findAvailablePlayers(Server server, Collection<String> assignedUuids, int limit) {
        Query query = new Query();
        if (assignedUuids != null && !assignedUuids.isEmpty()) {
            query.addCriteria(MongoQueries.where(PlayerFields.MINECRAFT_UUID).nin(assignedUuids));
        }
        query.limit(limit);
        return find(server, query);
    }

    public List<Player> findByLinkedBanId(Server server, String parentPunishmentId) {
        return find(server, linkedBanQuery(parentPunishmentId));
    }

    private Query linkedBanQuery(String parentPunishmentId) {
        return Query.query(MongoQueries.where(PlayerFields.PUNISHMENTS).elemMatch(
            Criteria.where(PunishmentFields.TYPE_ORDINAL).is(4)
                .and(PunishmentFields.DATA_LINKED_BAN_ID).is(parentPunishmentId)
        ));
    }

    public List<Player> findByLinkedBanId(String databaseName, String parentPunishmentId) {
        return find(databaseName, linkedBanQuery(parentPunishmentId));
    }

    public void updateLoginState(Server server, Player player) {
        Update update = new Update()
            .set(PlayerFields.USERNAMES, player.getUsernames())
            .set(PlayerFields.IP_ADDRESSES, player.getIpAddresses())
            .set(PlayerFields.DATA, player.getData());
        updateById(server, player.getId(), update);
    }

    private void updateById(Server server, String playerId, Update update) {
        Query query = Query.query(MongoQueries.where(PlayerFields.ID).is(playerId));
        updateFirst(server, query, update);
    }

    public void replaceUsernames(Server server, Player player) {
        updateById(server, player.getId(), new Update().set(PlayerFields.USERNAMES, player.getUsernames()));
    }

    public void replaceNotes(Server server, Player player) {
        updateById(server, player.getId(), new Update().set(PlayerFields.NOTES, player.getNotes()));
    }

    public void replaceIpAddresses(Server server, Player player) {
        updateById(server, player.getId(), new Update().set(PlayerFields.IP_ADDRESSES, player.getIpAddresses()));
    }

    public void replacePendingNotifications(Server server, Player player, List<Map<String, Object>> notifications) {
        updateById(server, player.getId(), new Update().set(PlayerFields.DATA_PENDING_NOTIFICATIONS, notifications));
    }

    public void replaceData(Server server, Player player) {
        updateById(server, player.getId(), new Update().set(PlayerFields.DATA, player.getData()));
    }

    public void replaceLinkedAccounts(Server server, Player player) {
        Map<String, Object> data = player.getData();
        Object linkedAccounts = data != null ? data.get("linkedAccounts") : null;
        Object lastLinkedUpdate = data != null ? data.get("lastLinkedUpdate") : null;

        Update update = new Update().set(PlayerFields.DATA_LINKED_ACCOUNTS, linkedAccounts);
        if (lastLinkedUpdate instanceof Date date) {
            update.set(PlayerFields.DATA_LAST_LINKED_UPDATE, date);
        } else {
            update.unset(PlayerFields.DATA_LAST_LINKED_UPDATE);
        }
        updateById(server, player.getId(), update);
    }

    public void replacePunishments(Server server, Player player) {
        Query query = Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).is(player.getMinecraftUuid().toString()));
        Update update = new Update().set(PlayerFields.PUNISHMENTS, player.getPunishments());
        updateFirst(server, query, update);
    }

    public void replacePunishments(String databaseName, Player player) {
        Query query = Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).is(player.getMinecraftUuid().toString()));
        Update update = new Update().set(PlayerFields.PUNISHMENTS, player.getPunishments());
        updateFirst(databaseName, query, update);
    }

    public boolean markDisconnected(Server server, String minecraftUuid, long sessionDurationMs) {
        Query query = Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).is(minecraftUuid));
        Update update = new Update()
            .set(PlayerFields.DATA_IS_ONLINE, false)
            .set(PlayerFields.DATA_LAST_LOGOUT, new Date());

        if (sessionDurationMs > 0) {
            update.inc(PlayerFields.DATA_TOTAL_PLAYTIME_SECONDS, sessionDurationMs / 1000);
        }

        return updateFirst(server, query, update).getMatchedCount() > 0;
    }

    public boolean updateLastServer(Server server, String minecraftUuid, String serverName) {
        Query query = Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).is(minecraftUuid));
        Update update = new Update().set(PlayerFields.DATA_LAST_SERVER, serverName);
        return updateFirst(server, query, update).getMatchedCount() > 0;
    }

    public void markStalePlayersOffline(Server server, Criteria staleOnlineCriteria, Date logoutTime) {
        Query query = Query.query(staleOnlineCriteria);
        Update update = new Update()
            .set(PlayerFields.DATA_IS_ONLINE, false)
            .set(PlayerFields.DATA_LAST_LOGOUT, logoutTime);
        updateMulti(server, query, update);
    }

    public List<Player> findByMinecraftUuids(Server server, List<UUID> minecraftUuids) {
        if (minecraftUuids == null || minecraftUuids.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).in(minecraftUuids));
        return find(server, query);
    }

    public void insertAll(Server server, List<Player> players) {
        serverTemplate(server).insertAll(players);
    }

    public org.springframework.data.mongodb.core.BulkOperations bulkOps(Server server) {
        return serverTemplate(server).bulkOps(
            org.springframework.data.mongodb.core.BulkOperations.BulkMode.UNORDERED,
            Player.class,
            CollectionName.PLAYERS
        );
    }

    public Punishment readPunishment(Server server, Document punishmentDocument) {
        return serverTemplate(server).getConverter().read(Punishment.class, punishmentDocument);
    }

    public Map<String, Integer> countPunishmentsByIssuerName(Server server) {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.unwind(PlayerFields.PUNISHMENTS),
            Aggregation.group(PlayerFields.PUNISHMENT_ISSUER_NAME).count().as("count")
        );
        AggregationResults<Document> results = aggregate(server, aggregation, Document.class);
        Map<String, Integer> punishmentCounts = new LinkedHashMap<>();
        for (Document document : results.getMappedResults()) {
            String issuerName = document.getString("_id");
            if (issuerName != null) {
                punishmentCounts.put(issuerName, document.getInteger("count", 0));
            }
        }
        return punishmentCounts;
    }

    public void updatePunishmentField(Server server, String playerUuid, String punishmentId, Update update) {
        Query query = Query.query(
            Criteria.where("minecraftUuid").is(playerUuid)
                .and("punishments.id").is(punishmentId)
        );
        updateFirst(server, query, update);
    }
}
