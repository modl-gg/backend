package gg.modl.backend.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.service.ReplayDeletionService.ReplayDeletionOutcome;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplayDeletionServiceTest {
    private static final String REPLAY_KEY = "db/replays/r1.modlreplay";
    private static final String EVIDENCE_KEY = "db/evidence/e1.png";

    private ReplayMongoRepository replayRepository;
    private S3StorageService s3StorageService;
    private StorageMetadataService storageMetadataService;
    private TicketMongoRepository ticketRepository;
    private ReplayDeletionService service;
    private Server server;

    @BeforeEach
    void setUp() {
        replayRepository = mock(ReplayMongoRepository.class);
        s3StorageService = mock(S3StorageService.class);
        storageMetadataService = mock(StorageMetadataService.class);
        ticketRepository = mock(TicketMongoRepository.class);
        service = new ReplayDeletionService(replayRepository, s3StorageService, storageMetadataService, ticketRepository);
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    }

    @Test
    void deleteReplayWithStorageRemovesFileMetadataRecordAndTicketReferences() {
        ReplayDocument replay = replay("r1", REPLAY_KEY);
        when(s3StorageService.deleteFile(REPLAY_KEY)).thenReturn(true);
        when(storageMetadataService.removeFile(server, REPLAY_KEY)).thenReturn(true);
        when(replayRepository.deleteByReplayId(server, "r1")).thenReturn(true);

        ReplayDeletionOutcome outcome = service.deleteReplayWithStorage(server, replay);

        assertEquals(ReplayDeletionOutcome.DELETED, outcome);
        verify(s3StorageService).deleteFile(REPLAY_KEY);
        verify(storageMetadataService).removeFile(server, REPLAY_KEY);
        verify(replayRepository).deleteByReplayId(server, "r1");
        verify(ticketRepository).clearReplayReferences(server, List.of("r1"));
    }

    @Test
    void deleteReplayWithStorageStopsWhenStorageDeleteFails() {
        ReplayDocument replay = replay("r1", REPLAY_KEY);
        when(s3StorageService.deleteFile(REPLAY_KEY)).thenReturn(false);

        ReplayDeletionOutcome outcome = service.deleteReplayWithStorage(server, replay);

        assertEquals(ReplayDeletionOutcome.STORAGE_DELETE_FAILED, outcome);
        verify(storageMetadataService, never()).removeFile(any(), any());
        verify(replayRepository, never()).deleteByReplayId(any(), any());
        verify(ticketRepository, never()).clearReplayReferences(any(), any());
    }

    @Test
    void deleteReplayWithStorageStopsWhenMetadataRemoveFails() {
        ReplayDocument replay = replay("r1", REPLAY_KEY);
        when(s3StorageService.deleteFile(REPLAY_KEY)).thenReturn(true);
        when(storageMetadataService.removeFile(server, REPLAY_KEY)).thenReturn(false);

        ReplayDeletionOutcome outcome = service.deleteReplayWithStorage(server, replay);

        assertEquals(ReplayDeletionOutcome.METADATA_REMOVE_FAILED, outcome);
        verify(replayRepository, never()).deleteByReplayId(any(), any());
        verify(ticketRepository, never()).clearReplayReferences(any(), any());
    }

    @Test
    void deleteReplayWithStorageStillClearsTicketsWhenRecordAlreadyAbsent() {
        ReplayDocument replay = replay("r1", REPLAY_KEY);
        when(s3StorageService.deleteFile(REPLAY_KEY)).thenReturn(true);
        when(storageMetadataService.removeFile(server, REPLAY_KEY)).thenReturn(true);
        when(replayRepository.deleteByReplayId(server, "r1")).thenReturn(false);

        ReplayDeletionOutcome outcome = service.deleteReplayWithStorage(server, replay);

        assertEquals(ReplayDeletionOutcome.ALREADY_ABSENT, outcome);
        verify(ticketRepository).clearReplayReferences(server, List.of("r1"));
    }

    @Test
    void reconcileDeletedStorageKeysPurgesMatchingReplayRecordsAndTicketReferences() {
        when(replayRepository.findByStorageKeys(server, List.of(REPLAY_KEY)))
            .thenReturn(List.of(replay("r1", REPLAY_KEY)));
        when(replayRepository.deleteByReplayIds(server, List.of("r1"))).thenReturn(1L);
        when(ticketRepository.clearReplayReferences(server, List.of("r1"))).thenReturn(1L);

        int removed = service.reconcileDeletedStorageKeys(server, List.of(REPLAY_KEY, EVIDENCE_KEY));

        assertEquals(1, removed);
        verify(replayRepository).findByStorageKeys(server, List.of(REPLAY_KEY));
        verify(replayRepository).deleteByReplayIds(server, List.of("r1"));
        verify(ticketRepository).clearReplayReferences(server, List.of("r1"));
    }

    @Test
    void reconcileDeletedStorageKeysIgnoresNonReplayKeys() {
        int removed = service.reconcileDeletedStorageKeys(server, List.of(EVIDENCE_KEY));

        assertEquals(0, removed);
        verify(replayRepository, never()).findByStorageKeys(any(), any());
        verify(replayRepository, never()).deleteByReplayIds(any(), any());
        verify(ticketRepository, never()).clearReplayReferences(any(), any());
    }

    @Test
    void reconcileDeletedStorageKeysNoOpsWhenNoReplayRecordsMatch() {
        when(replayRepository.findByStorageKeys(eq(server), any())).thenReturn(List.of());

        int removed = service.reconcileDeletedStorageKeys(server, List.of(REPLAY_KEY));

        assertEquals(0, removed);
        verify(replayRepository).findByStorageKeys(server, List.of(REPLAY_KEY));
        verify(replayRepository, never()).deleteByReplayIds(any(), any());
        verify(ticketRepository, never()).clearReplayReferences(any(), any());
    }

    @Test
    void reconcileDeletedStorageKeysSwallowsRepositoryFailures() {
        when(replayRepository.findByStorageKeys(eq(server), any())).thenThrow(new RuntimeException("mongo unavailable"));

        int removed = service.reconcileDeletedStorageKeys(server, List.of(REPLAY_KEY));

        assertEquals(0, removed);
        verify(replayRepository, never()).deleteByReplayIds(any(), any());
        verify(ticketRepository, never()).clearReplayReferences(any(), any());
    }

    private ReplayDocument replay(String id, String storageKey) {
        ReplayDocument replay = new ReplayDocument();
        replay.setId(id);
        replay.setStorageKey(storageKey);
        replay.setStatus(ReplayDocument.STATUS_COMPLETE);
        return replay;
    }
}
