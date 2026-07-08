package gg.modl.backend.replay.service;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.replay.config.LegacyReplayCleanupProperties;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.ReplayRetentionSettings;
import gg.modl.backend.settings.service.ReplayRetentionSettingsService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyReplayCleanupService {
    private final LegacyReplayCleanupProperties properties;
    private final ServerMongoRepository serverRepository;
    private final ReplayMongoRepository replayRepository;
    private final ReplayRetentionSettingsService replayRetentionSettingsService;
    private final ReplayDeletionService replayDeletionService;
    private final Clock clock;

    private static final int MAX_PAGES_PER_RUN = 1000;

    @Scheduled(fixedDelayString = "${modl.replay.cleanup.interval-ms:3600000}")
    public void runScheduledCleanup() {
        runCleanupOnce();
    }

    public void runCleanupOnce() {
        if (!properties.isEnabled()) {
            return;
        }

        CleanupStats stats = new CleanupStats();
        List<Server> servers = serverRepository.findAll();
        for (Server server : servers) {
            try {
                processServer(server, stats);
            } catch (Exception e) {
                stats.failed++;
                log.error("Legacy replay cleanup failed for server {} - skipping", server != null ? server.getId() : "null", e);
            }
        }

        log.info(
            "Legacy replay cleanup servers={} scanned={} deleted={} skippedRetentionDisabled={} skippedMissingStorageKey={} storageDeleteFailures={} metadataRemoveFailures={} failedServers={}",
            servers.size(),
            stats.scanned,
            stats.deleted,
            stats.skippedRetentionDisabled,
            stats.skippedMissingStorageKey,
            stats.storageDeleteFailures,
            stats.metadataRemoveFailures,
            stats.failed
        );
    }

    private void processServer(Server server, CleanupStats stats) {
        if (server == null || server.getDatabaseName() == null || server.getDatabaseName().isBlank()) {
            return;
        }

        ReplayRetentionSettings settings = replayRetentionSettingsService.getReplayRetentionSettings(server);
        if (!settings.isEnabled()) {
            stats.skippedRetentionDisabled++;
            return;
        }

        Instant cutoffInstant = clock.instant().minus(Duration.ofDays(settings.getDays()));
        Date cutoff = Date.from(cutoffInstant);

        long missingStorageKey = replayRepository.countExpiredWithMissingStorageKey(server, cutoff);
        if (missingStorageKey > 0) {
            stats.skippedMissingStorageKey += missingStorageKey;
            log.warn(
                "Found {} expired replays with missing storageKey server={} - skipped",
                missingStorageKey,
                server.getId()
            );
        }

        int pageSize = Math.min(Math.max(properties.getBatchSize(), 1), 500);
        Date cursor = null;
        int pages = 0;
        while (pages++ < MAX_PAGES_PER_RUN) {
            List<ReplayDocument> page = (cursor == null)
                ? replayRepository.findExpiredCompletedOrFailed(server, cutoff, pageSize)
                : replayRepository.findExpiredCompletedOrFailedAfter(server, cutoff, cursor, pageSize);
            if (page.isEmpty()) {
                break;
            }

            for (ReplayDocument replay : page) {
                stats.scanned++;
                switch (replayDeletionService.deleteReplayWithStorage(server, replay)) {
                    case DELETED -> stats.deleted++;
                    case STORAGE_DELETE_FAILED -> stats.storageDeleteFailures++;
                    case METADATA_REMOVE_FAILED -> stats.metadataRemoveFailures++;
                    case ALREADY_ABSENT -> { }
                }
            }

            // Advance the cursor past the page's max createdAt over ALL rows (including retained/failed
            // ones) so a persistently-failing head object can no longer block forward progress.
            Date pageMax = page.stream()
                .map(ReplayDocument::getCreatedAt)
                .filter(Objects::nonNull)
                .max(Date::compareTo)
                .orElse(null);
            if (page.size() < pageSize) {
                break; // exhausted
            }
            if (pageMax == null || (cursor != null && !pageMax.after(cursor))) {
                break; // cannot advance (degenerate all-same-timestamp page) - retry next run
            }
            cursor = pageMax;
        }

        if (stats.storageDeleteFailures > 0 || stats.metadataRemoveFailures > 0) {
            log.warn(
                "Legacy replay cleanup encountered deletion failures server={} storageDeleteFailures={} metadataRemoveFailures={}",
                server.getId(),
                stats.storageDeleteFailures,
                stats.metadataRemoveFailures
            );
        }
    }

    private static class CleanupStats {
        private long scanned;
        private long deleted;
        private long skippedRetentionDisabled;
        private long skippedMissingStorageKey;
        private long storageDeleteFailures;
        private long metadataRemoveFailures;
        private long failed;
    }
}
