package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.server.data.Server;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ServerDatabaseMongoRepository {
    private final TenantMongoAccess tenantMongoAccess;

    public Optional<ServerDatabaseStats> readStats(Server server) {
        try {
            MongoTemplate template = tenantMongoAccess.forServer(server);
            long players = template.count(new Query(), CollectionName.PLAYERS);
            long tickets = template.count(new Query(), CollectionName.TICKETS);
            long logs = template.count(new Query(), CollectionName.LOGS);
            Document dbStats = template.getDb().runCommand(new Document("dbStats", 1));
            long storageSize = extractLong(dbStats, "storageSize");
            return Optional.of(new ServerDatabaseStats(players, tickets, logs, storageSize));
        } catch (Exception e) {
            log.warn("Failed to read stats for server database {}", server.getDatabaseName(), e);
            return Optional.empty();
        }
    }

    private long extractLong(Document document, String fieldName) {
        Object value = document.get(fieldName);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public Optional<UsageCounts> readUsageCounts(Server server) {
        try {
            MongoTemplate template = tenantMongoAccess.forServer(server);
            long players = template.count(new Query(), CollectionName.PLAYERS);
            long tickets = template.count(new Query(), CollectionName.TICKETS);
            return Optional.of(new UsageCounts(players, tickets));
        } catch (Exception e) {
            log.warn("Failed to read usage counts for server database {}", server.getDatabaseName(), e);
            return Optional.empty();
        }
    }

    public boolean dropDatabase(Server server) {
        try {
            tenantMongoAccess.forServer(server).getDb().drop();
            return true;
        } catch (Exception e) {
            log.error("Failed to drop database {}", server.getDatabaseName(), e);
            return false;
        }
    }

    public record UsageCounts(long players, long tickets) {}

    public record ServerDatabaseStats(long players, long tickets, long logs, long storageSize) {}
}
