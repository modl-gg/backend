package gg.modl.backend.replaylite.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.replaylite.data.ReplayLiteDocument;
import gg.modl.backend.replaylite.repository.ReplayLiteMongoRepository;
import gg.modl.backend.replaylite.storage.ReplayLiteStorageService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplayLiteCleanupServiceTest {
    @Mock
    private ReplayLiteMongoRepository repository;

    @Mock
    private ReplayLiteStorageService storageService;

    private ReplayLiteCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new ReplayLiteCleanupService(repository, storageService);
    }

    @Test
    void cleanupRetainsMetadataWhenObjectDeletionFails() {
        ReplayLiteDocument document = document("replay-1", "replay-lite/20260511/replay-1.modlreplay");
        when(repository.findExpiredConfirmed(org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of(document));
        when(repository.findStalePending(org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of());
        when(storageService.deleteObject(document.getObjectKey())).thenReturn(false);

        cleanupService.cleanupExpiredReplays();

        verify(repository, never()).deleteByReplayId(document.getId());
    }

    @Test
    void cleanupDeletesMetadataWhenObjectDeletionSucceeds() {
        ReplayLiteDocument document = document("replay-1", "replay-lite/20260511/replay-1.modlreplay");
        when(repository.findExpiredConfirmed(org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of(document));
        when(repository.findStalePending(org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of());
        when(storageService.deleteObject(document.getObjectKey())).thenReturn(true);

        cleanupService.cleanupExpiredReplays();

        verify(repository).deleteByReplayId(document.getId());
    }

    @Test
    void cleanupDeletesMetadataWhenNoObjectKeyExists() {
        ReplayLiteDocument document = document("replay-1", "");
        when(repository.findExpiredConfirmed(org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of(document));
        when(repository.findStalePending(org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(List.of());

        cleanupService.cleanupExpiredReplays();

        verify(storageService, never()).deleteObject(org.mockito.ArgumentMatchers.any());
        verify(repository).deleteByReplayId(document.getId());
    }

    private ReplayLiteDocument document(String id, String objectKey) {
        ReplayLiteDocument document = new ReplayLiteDocument();
        document.setId(id);
        document.setObjectKey(objectKey);
        return document;
    }
}
