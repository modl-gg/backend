package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ServerDatabaseMongoRepository {
    private final TenantMongoAccess tenantMongoAccess;

    public Optional<ServerDatabaseStats> readStats(Server server) {
        try {
            var template = tenantMongoAccess.forServer(server);
            long players = template.count(new Query(), CollectionName.PLAYERS);
            long tickets = template.count(new Query(), CollectionName.TICKETS);
            long logs = template.count(new Query(), CollectionName.LOGS);
            Document dbStats = template.getDb().runCommand(new Document("dbStats", 1));
            long storageSize = extractLong(dbStats, "storageSize");
            return Optional.of(new ServerDatabaseStats(players, tickets, logs, storageSize));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public Optional<UsageCounts> readUsageCounts(Server server) {
        try {
            var template = tenantMongoAccess.forServer(server);
            long players = template.count(new Query(), CollectionName.PLAYERS);
            long tickets = template.count(new Query(), CollectionName.TICKETS);
            return Optional.of(new UsageCounts(players, tickets));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public boolean dropDatabase(Server server) {
        try {
            tenantMongoAccess.forServer(server).getDb().drop();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private long extractLong(Document document, String fieldName) {
        Object value = document.get(fieldName);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public record UsageCounts(long players, long tickets) {}

    public record ServerDatabaseStats(long players, long tickets, long logs, long storageSize) {}
}
