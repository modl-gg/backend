package gg.modl.backend.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.database.mongo.repository.TrainingSegmentRepository;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.service.ReplayDeletionService.ReplayBatchDeletionResult;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplayDeletionServiceTest {
    private static final String R1_KEY = "db/replays/r1.modlreplay";
    private static final String R2_KEY = "db/replays/r2.modlreplay";
    private static final String EVIDENCE_KEY = "db/evidence/e1.png";

    private ReplayMongoRepository replayRepository;
    private S3StorageService s3StorageService;
    private StorageMetadataService storageMetadataService;
    private TicketMongoRepository ticketRepository;
    private TrainingSegmentRepository trainingSegmentRepository;
    private ReplayDeletionService service;
    private Server server;

    @BeforeEach
    void setUp() {
        replayRepository = mock(ReplayMongoRepository.class);
        s3StorageService = mock(S3StorageService.class);
        storageMetadataService = mock(StorageMetadataService.class);
        ticketRepository = mock(TicketMongoRepository.class);
        trainingSegmentRepository = mock(TrainingSegmentRepository.class);
        service = new ReplayDeletionService(
            replayRepository,
            s3StorageService,
            storageMetadataService,
            ticketRepository,
            trainingSegmentRepository
        );
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    }

    @Test
    void deleteReplaysWithStorageBulkDeletesStorageThenPurgesRecords() {
        when(replayRepository.deleteByReplayIds(server, List.of("r1", "r2"))).thenReturn(2L);

        ReplayBatchDeletionResult result = service.deleteReplaysWithStorage(
            server,
            List.of(replay("r1", R1_KEY), replay("r2", R2_KEY))
        );

        assertEquals(2, result.requested());
        assertEquals(2L, result.deleted());
        assertEquals(0L, result.alreadyAbsent());
        verify(s3StorageService).bulkDelete(List.of(R1_KEY, R2_KEY));
        verify(storageMetadataService).removeFiles(server, List.of(R1_KEY, R2_KEY));
        verify(replayRepository).deleteByReplayIds(server, List.of("r1", "r2"));
        verify(trainingSegmentRepository).deleteByReplayIds("db", List.of("r1", "r2"));
        verify(ticketRepository).clearReplayReferences(server, List.of("r1", "r2"));
    }

    @Test
    void deleteReplaysWithStorageReportsAlreadyAbsentWhenFewerRecordsRemoved() {
        when(replayRepository.deleteByReplayIds(server, List.of("r1", "r2"))).thenReturn(1L);

        ReplayBatchDeletionResult result = service.deleteReplaysWithStorage(
            server,
            List.of(replay("r1", R1_KEY), replay("r2", R2_KEY))
        );

        assertEquals(2, result.requested());
        assertEquals(1L, result.deleted());
        assertEquals(1L, result.alreadyAbsent());
    }

    @Test
    void deleteReplaysWithStorageSkipsReplaysWithoutId() {
        when(replayRepository.deleteByReplayIds(server, List.of("r1"))).thenReturn(1L);

        ReplayBatchDeletionResult result = service.deleteReplaysWithStorage(
            server,
            List.of(replay("r1", R1_KEY), replay(null, R2_KEY))
        );

        assertEquals(1, result.requested());
        verify(s3StorageService).bulkDelete(List.of(R1_KEY));
        verify(replayRepository).deleteByReplayIds(server, List.of("r1"));
    }

    @Test
    void deleteReplaysWithStorageSkipsStorageDeletionWhenNoStorageKeys() {
        when(replayRepository.deleteByReplayIds(server, List.of("r1"))).thenReturn(1L);

        ReplayBatchDeletionResult result = service.deleteReplaysWithStorage(server, List.of(replay("r1", "  ")));

        assertEquals(1, result.requested());
        verify(s3StorageService, never()).bulkDelete(anyList());
        verify(storageMetadataService, never()).removeFiles(any(), anyList());
        verify(replayRepository).deleteByReplayIds(server, List.of("r1"));
    }

    @Test
    void deleteReplaysWithStorageReturnsEmptyResultForEmptyBatch() {
        ReplayBatchDeletionResult nullResult = service.deleteReplaysWithStorage(server, null);
        ReplayBatchDeletionResult emptyResult = service.deleteReplaysWithStorage(server, List.of());

        assertEquals(0, nullResult.requested());
        assertEquals(0L, nullResult.deleted());
        assertEquals(0, emptyResult.requested());
        verifyNoInteractions(s3StorageService, storageMetadataService, trainingSegmentRepository);
        verify(replayRepository, never()).deleteByReplayIds(any(), anyList());
    }

    @Test
    void reconcileDeletedStorageKeysPurgesMatchingReplayRecordsAndTicketReferences() {
        when(replayRepository.findByStorageKeys(server, List.of(R1_KEY)))
            .thenReturn(List.of(replay("r1", R1_KEY)));
        when(replayRepository.deleteByReplayIds(server, List.of("r1"))).thenReturn(1L);
        when(ticketRepository.clearReplayReferences(server, List.of("r1"))).thenReturn(1L);

        int removed = service.reconcileDeletedStorageKeys(server, List.of(R1_KEY, EVIDENCE_KEY));

        assertEquals(1, removed);
        verify(replayRepository).findByStorageKeys(server, List.of(R1_KEY));
        verify(replayRepository).deleteByReplayIds(server, List.of("r1"));
        verify(trainingSegmentRepository).deleteByReplayIds("db", List.of("r1"));
        verify(ticketRepository).clearReplayReferences(server, List.of("r1"));
    }

    @Test
    void reconcileDeletedStorageKeysIgnoresNonReplayKeys() {
        int removed = service.reconcileDeletedStorageKeys(server, List.of(EVIDENCE_KEY));

        assertEquals(0, removed);
        verify(replayRepository, never()).findByStorageKeys(any(), anyList());
        verify(replayRepository, never()).deleteByReplayIds(any(), anyList());
        verify(ticketRepository, never()).clearReplayReferences(any(), anyList());
    }

    @Test
    void reconcileDeletedStorageKeysNoOpsWhenNoReplayRecordsMatch() {
        when(replayRepository.findByStorageKeys(eq(server), anyList())).thenReturn(List.of());

        int removed = service.reconcileDeletedStorageKeys(server, List.of(R1_KEY));

        assertEquals(0, removed);
        verify(replayRepository).findByStorageKeys(server, List.of(R1_KEY));
        verify(replayRepository, never()).deleteByReplayIds(any(), anyList());
        verify(ticketRepository, never()).clearReplayReferences(any(), anyList());
    }

    @Test
    void reconcileDeletedStorageKeysSwallowsRepositoryFailures() {
        when(replayRepository.findByStorageKeys(eq(server), anyList())).thenThrow(new RuntimeException("mongo unavailable"));

        int removed = service.reconcileDeletedStorageKeys(server, List.of(R1_KEY));

        assertEquals(0, removed);
        verify(replayRepository, never()).deleteByReplayIds(any(), anyList());
        verify(ticketRepository, never()).clearReplayReferences(any(), anyList());
    }

    private ReplayDocument replay(String id, String storageKey) {
        ReplayDocument replay = new ReplayDocument();
        replay.setId(id);
        replay.setStorageKey(storageKey);
        replay.setStatus(ReplayDocument.STATUS_COMPLETE);
        return replay;
    }
}
