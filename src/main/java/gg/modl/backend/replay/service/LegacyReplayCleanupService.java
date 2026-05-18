package gg.modl.backend.replay.service;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.replay.config.LegacyReplayCleanupProperties;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.ReplayRetentionSettings;
import gg.modl.backend.settings.service.ReplayRetentionSettingsService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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
    private final S3StorageService s3StorageService;
    private final StorageMetadataService storageMetadataService;
    private final Clock clock;

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
            processServer(server, stats);
        }

        log.info(
            "Legacy replay cleanup servers={} scanned={} deleted={} skippedRetentionDisabled={} skippedMissingStorageKey={}",
            servers.size(),
            stats.scanned,
            stats.deleted,
            stats.skippedRetentionDisabled,
            stats.skippedMissingStorageKey
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
        List<ReplayDocument> expired = replayRepository.findExpiredCompletedOrFailed(server, cutoff, properties.getBatchSize());
        for (ReplayDocument replay : expired) {
            stats.scanned++;
            if (replay.getStorageKey() == null || replay.getStorageKey().isBlank()) {
                stats.skippedMissingStorageKey++;
                replayRepository.deleteByReplayId(server, replay.getId());
                continue;
            }

            boolean storageDeleted = s3StorageService.deleteFile(replay.getStorageKey());
            if (!storageDeleted) {
                continue;
            }

            boolean metadataRemoved = storageMetadataService.removeFile(server, replay.getStorageKey());
            if (!metadataRemoved) {
                continue;
            }

            if (replayRepository.deleteByReplayId(server, replay.getId())) {
                stats.deleted++;
            }
        }
    }

    private static class CleanupStats {
        private int scanned;
        private int deleted;
        private int skippedRetentionDisabled;
        private int skippedMissingStorageKey;
    }
}
