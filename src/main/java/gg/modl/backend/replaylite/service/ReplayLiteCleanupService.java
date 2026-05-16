package gg.modl.backend.replaylite.service;

import gg.modl.backend.replaylite.data.ReplayLiteDocument;
import gg.modl.backend.replaylite.repository.ReplayLiteMongoRepository;
import gg.modl.backend.replaylite.storage.ReplayLiteStorageService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplayLiteCleanupService {
    private static final int CLEANUP_BATCH_SIZE = 250;
    private static final Duration PENDING_UPLOAD_TTL = Duration.ofMinutes(15);

    private final ReplayLiteMongoRepository repository;
    private final ReplayLiteStorageService storageService;
    private final Clock clock = Clock.systemUTC();

    @Scheduled(fixedDelayString = "${modl.replay-lite.cleanup-delay-ms:300000}")
    public void cleanupExpiredReplays() {
        Instant now = clock.instant();
        cleanup(repository.findExpiredConfirmed(now, CLEANUP_BATCH_SIZE));
        cleanup(repository.findStalePending(now.minus(PENDING_UPLOAD_TTL), CLEANUP_BATCH_SIZE));
    }

    private void cleanup(Iterable<ReplayLiteDocument> documents) {
        for (ReplayLiteDocument document : documents) {
            if (document.getObjectKey() != null && !document.getObjectKey().isBlank()) {
                if (!storageService.deleteObject(document.getObjectKey())) {
                    log.warn("Retaining Replay Lite metadata {} because object deletion failed", document.getId());
                    continue;
                }
            }
            repository.deleteByReplayId(document.getId());
            log.debug("Cleaned Replay Lite replay {}", document.getId());
        }
    }
}
