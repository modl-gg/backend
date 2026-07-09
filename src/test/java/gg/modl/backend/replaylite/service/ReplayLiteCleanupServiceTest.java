package gg.modl.backend.replaylite.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.infrastructure.scheduling.SchedulerLeaseService;
import gg.modl.backend.replaylite.data.ReplayLiteDocument;
import gg.modl.backend.replaylite.repository.ReplayLiteMongoRepository;
import gg.modl.backend.replaylite.storage.ReplayLiteStorageService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplayLiteCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-15T12:00:00Z");
    private static final int BATCH_SIZE = 250;

    @Mock
    private ReplayLiteMongoRepository repository;

    @Mock
    private ReplayLiteStorageService storageService;

    @Mock
    private SchedulerLeaseService schedulerLeaseService;

    private ReplayLiteCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        when(schedulerLeaseService.tryAcquire(anyString(), any())).thenReturn(true);
        cleanupService = new ReplayLiteCleanupService(
            repository,
            storageService,
            schedulerLeaseService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void cleanupRetainsMetadataWhenObjectDeletionFails() {
        ReplayLiteDocument document = document("replay-1", "replay-lite/20260511/replay-1.modlreplay");
        when(repository.findExpiredConfirmed(eq(NOW), isNull(), eq(BATCH_SIZE))).thenReturn(List.of(document));
        when(storageService.deleteObject(document.getObjectKey())).thenReturn(false);

        cleanupService.cleanupExpiredReplays();

        verify(repository, never()).deleteByReplayId(document.getId());
    }

    @Test
    void cleanupDeletesMetadataWhenObjectDeletionSucceeds() {
        ReplayLiteDocument document = document("replay-1", "replay-lite/20260511/replay-1.modlreplay");
        when(repository.findExpiredConfirmed(eq(NOW), isNull(), eq(BATCH_SIZE))).thenReturn(List.of(document));
        when(storageService.deleteObject(document.getObjectKey())).thenReturn(true);

        cleanupService.cleanupExpiredReplays();

        verify(repository).deleteByReplayId(document.getId());
    }

    @Test
    void cleanupDeletesMetadataWhenNoObjectKeyExists() {
        ReplayLiteDocument document = document("replay-1", "");
        when(repository.findExpiredConfirmed(eq(NOW), isNull(), eq(BATCH_SIZE))).thenReturn(List.of(document));

        cleanupService.cleanupExpiredReplays();

        verify(storageService, never()).deleteObject(any());
        verify(repository).deleteByReplayId(document.getId());
    }

    @Test
    void cleanupQueriesConfirmedExpiryAtNowAndStalePendingFifteenMinutesEarlier() {
        cleanupService.cleanupExpiredReplays();

        verify(repository).findExpiredConfirmed(eq(NOW), isNull(), eq(BATCH_SIZE));
        verify(repository).findStalePending(eq(NOW.minus(Duration.ofMinutes(15))), isNull(), eq(BATCH_SIZE));
    }

    private ReplayLiteDocument document(String id, String objectKey) {
        ReplayLiteDocument document = new ReplayLiteDocument();
        document.setId(id);
        document.setObjectKey(objectKey);
        return document;
    }
}
