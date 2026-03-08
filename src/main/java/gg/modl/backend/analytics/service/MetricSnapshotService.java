package gg.modl.backend.analytics.service;

import gg.modl.backend.analytics.data.MetricSnapshot;
import gg.modl.backend.database.mongo.repository.MetricSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricSnapshotService {
    private final ServerMongoRepository serverRepository;
    private final MetricSnapshotMongoRepository metricSnapshotRepository;

    @Scheduled(cron = "0 */5 * * * *")
    public void takeSnapshot() {
        try {
            Instant nowInstant = Instant.now();
            Date now = Date.from(nowInstant);
            // Truncate to 5-minute boundary
            long epochSeconds = nowInstant.getEpochSecond();
            Date fiveTruncated = Date.from(Instant.ofEpochSecond((epochSeconds / 300) * 300));
            Date fiveMinutesAgo = Date.from(nowInstant.minus(5, ChronoUnit.MINUTES));

            long totalServers = serverRepository.countAll();
            long activeServers = serverRepository.countActiveSince(fiveMinutesAgo);
            long totalPlayers = serverRepository.getUsageTotals().totalUsers();
            long onlinePlayers = serverRepository.sumOnlinePlayersSince(fiveMinutesAgo);

            metricSnapshotRepository.upsertSnapshot(
                    fiveTruncated,
                    activeServers,
                    totalServers,
                    totalPlayers,
                    onlinePlayers,
                    now
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
            Date oneDayAgo = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
            metricSnapshotRepository.deleteOlderThan(oneDayAgo);
            log.info("Purged metric snapshots older than 24 hours");
        } catch (Exception e) {
            log.error("Failed to purge old metric snapshots", e);
        }
    }
}
