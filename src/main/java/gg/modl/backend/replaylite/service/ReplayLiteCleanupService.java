package gg.modl.backend.replaylite.service;

import gg.modl.backend.infrastructure.scheduling.SchedulerLeaseService;
import gg.modl.backend.replaylite.data.ReplayLiteDocument;
import gg.modl.backend.replaylite.repository.ReplayLiteMongoRepository;
import gg.modl.backend.replaylite.repository.ReplayLiteMongoRepository.ReplayLiteCursor;
import gg.modl.backend.replaylite.storage.ReplayLiteStorageService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplayLiteCleanupService {
    private static final int CLEANUP_BATCH_SIZE = 250;
    private static final int MAX_BATCHES_PER_RUN = 40;
    private static final Duration PENDING_UPLOAD_TTL = Duration.ofMinutes(15);
    private static final Duration CLEANUP_LEASE_TTL = Duration.ofMinutes(30);
    private static final String CLEANUP_LEASE = "replay-lite-cleanup";

    private final ReplayLiteMongoRepository repository;
    private final ReplayLiteStorageService storageService;
    private final ReplayLiteService replayLiteService;
    private final SchedulerLeaseService schedulerLeaseService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${modl.replay-lite.cleanup-delay-ms:300000}")
    public void cleanupExpiredReplays() {
        if (!schedulerLeaseService.tryAcquire(CLEANUP_LEASE, CLEANUP_LEASE_TTL)) {
            log.debug("Skipping Replay Lite cleanup; lease held by another instance");
            return;
        }
        try {
            Instant now = clock.instant();
            drain((after, limit) -> repository.findExpiredConfirmed(now, after, limit), ReplayLiteDocument::getExpiresAt);
            Instant staleCutoff = now.minus(PENDING_UPLOAD_TTL);
            drain((after, limit) -> repository.findStalePending(staleCutoff, after, limit), ReplayLiteDocument::getCreatedAt);
        } finally {
            schedulerLeaseService.release(CLEANUP_LEASE);
        }
    }

    private void drain(ReplayLitePageFinder finder, Function<ReplayLiteDocument, Instant> sortValue) {
        ReplayLiteCursor cursor = null;
        int batches = 0;
        while (batches++ < MAX_BATCHES_PER_RUN) {
            List<ReplayLiteDocument> page = finder.find(cursor, CLEANUP_BATCH_SIZE);
            if (page.isEmpty()) {
                break;
            }
            cleanup(page);
            ReplayLiteDocument last = page.get(page.size() - 1);
            cursor = new ReplayLiteCursor(sortValue.apply(last), last.getId());
            if (page.size() < CLEANUP_BATCH_SIZE) {
                break;
            }
        }
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
            replayLiteService.releaseUnconfirmedDailyQuota(document);
            log.debug("Cleaned Replay Lite replay {}", document.getId());
        }
    }

    @FunctionalInterface
    private interface ReplayLitePageFinder {
        List<ReplayLiteDocument> find(ReplayLiteCursor after, int limit);
    }
}
