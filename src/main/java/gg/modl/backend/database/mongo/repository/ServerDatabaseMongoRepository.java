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
    private static final String SERVER_DATABASE_PREFIX = "server_";

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

    public PlayerCollectionInspection inspectPlayersCollection(Server server) {
        if (server.getDatabaseName() == null || server.getDatabaseName().isBlank()) {
            return PlayerCollectionInspection.unknown("missing_database_name");
        }
        if (!isServerDatabaseName(server.getDatabaseName())) {
            return PlayerCollectionInspection.unknown("unsafe_database_name");
        }

        try {
            MongoTemplate template = tenantMongoAccess.forServer(server);
            if (!template.collectionExists(CollectionName.PLAYERS)) {
                return PlayerCollectionInspection.safeMissing();
            }

            long players = template.count(new Query(), CollectionName.PLAYERS);
            if (players > 0) {
                return PlayerCollectionInspection.blockedNonEmpty(players);
            }

            return PlayerCollectionInspection.safeEmpty();
        } catch (Exception e) {
            log.warn("Failed to inspect players collection for server database {}", server.getDatabaseName(), e);
            return PlayerCollectionInspection.unknown("inspection_error");
        }
    }

    public boolean dropDatabase(Server server) {
        if (server.getDatabaseName() == null || server.getDatabaseName().isBlank()) {
            log.warn("Refusing to drop blank server database for server {}", server.getId());
            return false;
        }
        if (!isServerDatabaseName(server.getDatabaseName())) {
            log.warn("Refusing to drop unsafe server database {} for server {}", server.getDatabaseName(), server.getId());
            return false;
        }

        try {
            tenantMongoAccess.forServer(server).getDb().drop();
            return true;
        } catch (Exception e) {
            log.error("Failed to drop database {}", server.getDatabaseName(), e);
            return false;
        }
    }

    private boolean isServerDatabaseName(String databaseName) {
        return databaseName.startsWith(SERVER_DATABASE_PREFIX)
            && databaseName.length() > SERVER_DATABASE_PREFIX.length();
    }

    public record UsageCounts(long players, long tickets) {}

    public record ServerDatabaseStats(long players, long tickets, long logs, long storageSize) {}

    public record PlayerCollectionInspection(Status status, long players, String reason) {
        public enum Status {
            SAFE_EMPTY,
            BLOCKED_NON_EMPTY,
            UNKNOWN_ERROR
        }

        public static PlayerCollectionInspection safeMissing() {
            return new PlayerCollectionInspection(Status.SAFE_EMPTY, 0L, "missing_collection");
        }

        public static PlayerCollectionInspection safeEmpty() {
            return new PlayerCollectionInspection(Status.SAFE_EMPTY, 0L, "empty_collection");
        }

        public static PlayerCollectionInspection blockedNonEmpty(long players) {
            return new PlayerCollectionInspection(Status.BLOCKED_NON_EMPTY, players, "non_empty_collection");
        }

        public static PlayerCollectionInspection unknown(String reason) {
            return new PlayerCollectionInspection(Status.UNKNOWN_ERROR, 0L, reason);
        }
    }
}
