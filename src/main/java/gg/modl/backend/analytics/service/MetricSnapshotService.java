package gg.modl.backend.analytics.service;

import gg.modl.backend.analytics.data.MetricSnapshot;
import gg.modl.backend.database.CollectionName;
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

    @Scheduled(cron = "0 0 * * * *")
    public void takeSnapshot() {
        try {
            Date now = new Date();
            Date hourTruncated = Date.from(Instant.now().truncatedTo(ChronoUnit.HOURS));
            Date twentyFourHoursAgo = Date.from(Instant.now().minus(24, ChronoUnit.HOURS));

            long totalServers = serverRepository.count(new Query());

            long activeServers = serverRepository.count(
                    Query.query(MongoQueries.where(ServerFields.LAST_ACTIVITY_AT).gte(twentyFourHoursAgo))
            );

            Aggregation userCountAgg = Aggregation.newAggregation(
                    Aggregation.group().sum(ServerFields.USER_COUNT.path()).as("totalPlayers")
            );
            Document userCountResult = serverRepository.aggregate(userCountAgg, Document.class).getUniqueMappedResult();
            long totalPlayers = userCountResult != null
                    ? ((Number) userCountResult.getOrDefault("totalPlayers", 0L)).longValue()
                    : 0L;

            tenantMongoAccess.global().upsert(
                    Query.query(Criteria.where("date").is(hourTruncated)),
                    new Update()
                            .set("activeServers", activeServers)
                            .set("totalServers", totalServers)
                            .set("totalPlayers", totalPlayers)
                            .setOnInsert("createdAt", now),
                    MetricSnapshot.class
            );

            log.info("Metric snapshot saved: activeServers={}, totalServers={}, totalPlayers={}",
                    activeServers, totalServers, totalPlayers);
        } catch (Exception e) {
            log.error("Failed to take metric snapshot", e);
        }
    }
}
