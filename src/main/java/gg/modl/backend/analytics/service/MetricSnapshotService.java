package gg.modl.backend.analytics.service;

import gg.modl.backend.analytics.data.MetricSnapshot;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricSnapshotService {
    private final TenantMongoAccess tenantMongoAccess;
    private final ServerMongoRepository serverRepository;

    @Scheduled(cron = "0 */5 * * * *")
    public void takeSnapshot() {
        try {
            Instant nowInstant = Instant.now();
            Date now = Date.from(nowInstant);
            // Truncate to 5-minute boundary
            long epochSeconds = nowInstant.getEpochSecond();
            Date fiveTruncated = Date.from(Instant.ofEpochSecond((epochSeconds / 300) * 300));
            Date fiveMinutesAgo = Date.from(nowInstant.minus(5, ChronoUnit.MINUTES));

            long totalServers = serverRepository.count(new Query());

            long activeServers = serverRepository.count(
                    Query.query(MongoQueries.where(ServerFields.LAST_ACTIVITY_AT).gte(fiveMinutesAgo))
            );

            Aggregation userCountAgg = Aggregation.newAggregation(
                    Aggregation.group().sum(ServerFields.USER_COUNT.path()).as("totalPlayers")
            );
            Document userCountResult = serverRepository.aggregate(userCountAgg, Document.class).getUniqueMappedResult();
            long totalPlayers = userCountResult != null
                    ? ((Number) userCountResult.getOrDefault("totalPlayers", 0L)).longValue()
                    : 0L;

            Aggregation onlinePlayersAgg = Aggregation.newAggregation(
                    Aggregation.match(MongoQueries.where(ServerFields.LAST_ACTIVITY_AT).gte(fiveMinutesAgo)),
                    Aggregation.group().sum(ServerFields.ONLINE_PLAYER_COUNT.path()).as("onlinePlayers")
            );
            Document onlinePlayersResult = serverRepository.aggregate(onlinePlayersAgg, Document.class).getUniqueMappedResult();
            long onlinePlayers = onlinePlayersResult != null
                    ? ((Number) onlinePlayersResult.getOrDefault("onlinePlayers", 0L)).longValue()
                    : 0L;

            tenantMongoAccess.global().upsert(
                    Query.query(Criteria.where("date").is(fiveTruncated)),
                    new Update()
                            .set("activeServers", activeServers)
                            .set("totalServers", totalServers)
                            .set("totalPlayers", totalPlayers)
                            .set("onlinePlayers", onlinePlayers)
                            .setOnInsert("createdAt", now),
                    MetricSnapshot.class
            );

            log.info("Metric snapshot saved: activeServers={}, totalServers={}, totalPlayers={}, onlinePlayers={}",
                    activeServers, totalServers, totalPlayers, onlinePlayers);
        } catch (Exception e) {
            log.error("Failed to take metric snapshot", e);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeOldSnapshots() {
        try {
            Date sevenDaysAgo = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));
            tenantMongoAccess.global().remove(
                    Query.query(Criteria.where("date").lt(sevenDaysAgo)),
                    MetricSnapshot.class
            );
            log.info("Purged metric snapshots older than 7 days");
        } catch (Exception e) {
            log.error("Failed to purge old metric snapshots", e);
        }
    }
}
