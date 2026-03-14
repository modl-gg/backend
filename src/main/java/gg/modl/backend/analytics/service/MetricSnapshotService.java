package gg.modl.backend.analytics.service;

import gg.modl.backend.database.mongo.repository.MetricSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerInstanceSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricSnapshotService {
    private final ServerMongoRepository serverRepository;
    private final MetricSnapshotMongoRepository metricSnapshotRepository;
    private final ServerInstanceSnapshotMongoRepository serverInstanceSnapshotRepository;

    @Scheduled(cron = "0 */5 * * * *")
    public void takeSnapshot() {
        try {
            Instant nowInstant = Instant.now();
            Date now = Date.from(nowInstant);
            // Truncate to 5-minute boundary
            long epochSeconds = nowInstant.getEpochSecond();
            Date fiveTruncated = Date.from(Instant.ofEpochSecond((epochSeconds / 300) * 300));
            Date fiveMinutesAgo = Date.from(nowInstant.minus(5, ChronoUnit.MINUTES));

            long activeServers = serverRepository.countActiveSince(fiveMinutesAgo);

            metricSnapshotRepository.upsertSnapshot(
                fiveTruncated,
                activeServers,
                now
            );
        } catch (Exception e) {
            log.error("Failed to take metric snapshot", e);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeOldSnapshots() {
        try {
            Date oneDayAgo = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
            metricSnapshotRepository.deleteOlderThan(oneDayAgo);
            serverInstanceSnapshotRepository.deleteOlderThan(oneDayAgo);
            log.info("Purged metric snapshots and server instance snapshots older than 24 hours");
        } catch (Exception e) {
            log.error("Failed to purge old snapshots", e);
        }
    }
}
