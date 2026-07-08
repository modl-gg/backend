package gg.modl.backend.replay.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.replay.config.LegacyReplayCleanupProperties;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.service.ReplayDeletionService.ReplayDeletionOutcome;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.data.ReplayRetentionSettings;
import gg.modl.backend.settings.service.ReplayRetentionSettingsService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacyReplayCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-18T12:00:00Z");

    private ServerMongoRepository serverRepository;
    private ReplayMongoRepository replayRepository;
    private ReplayRetentionSettingsService replayRetentionSettingsService;
    private ReplayDeletionService replayDeletionService;
    private LegacyReplayCleanupService cleanupService;
    private Server server;

    @BeforeEach
    void setUp() {
        serverRepository = mock(ServerMongoRepository.class);
        replayRepository = mock(ReplayMongoRepository.class);
        replayRetentionSettingsService = mock(ReplayRetentionSettingsService.class);
        replayDeletionService = mock(ReplayDeletionService.class);
        LegacyReplayCleanupProperties properties = new LegacyReplayCleanupProperties();
        properties.setEnabled(true);
        properties.setBatchSize(100);
        cleanupService = new LegacyReplayCleanupService(
            properties,
            serverRepository,
            replayRepository,
            replayRetentionSettingsService,
            replayDeletionService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    }

    @Test
    void cleanupDelegatesExpiredReplayDeletionToReplayDeletionService() {
        ReplayDocument replay = replay("replay-1", "db/replays/replay-1.modlreplay");
        Date cutoff = Date.from(NOW.minus(Duration.ofDays(7)));

        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.countExpiredWithMissingStorageKey(server, cutoff)).thenReturn(0L);
        when(replayRepository.findExpiredCompletedOrFailed(server, cutoff, 100)).thenReturn(List.of(replay));
        when(replayDeletionService.deleteReplayWithStorage(server, replay)).thenReturn(ReplayDeletionOutcome.DELETED);

        cleanupService.runCleanupOnce();

        verify(replayDeletionService).deleteReplayWithStorage(server, replay);
    }

    @Test
    void cleanupReportsMissingStorageKeyCountAndDoesNotProcessBlankRecords() {
        Date cutoff = Date.from(NOW.minus(Duration.ofDays(7)));

        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.countExpiredWithMissingStorageKey(server, cutoff)).thenReturn(3L);
        when(replayRepository.findExpiredCompletedOrFailed(server, cutoff, 100)).thenReturn(List.of());

        cleanupService.runCleanupOnce();

        verify(replayRepository).countExpiredWithMissingStorageKey(server, cutoff);
        verify(replayDeletionService, never()).deleteReplayWithStorage(eq(server), any());
    }

    @Test
    void cleanupSkipsTenantWhenRetentionIsDisabled() {
        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(false, 7));

        cleanupService.runCleanupOnce();

        verify(replayRepository, never()).countExpiredWithMissingStorageKey(eq(server), any(Date.class));
        verify(replayRepository, never()).findExpiredCompletedOrFailed(eq(server), any(Date.class), anyInt());
        verify(replayDeletionService, never()).deleteReplayWithStorage(eq(server), any());
    }

    @Test
    void cleanupPagesPastPersistentlyFailingHeadObject() {
        LegacyReplayCleanupService pagingService = pagingService(2);
        Date cutoff = Date.from(NOW.minus(Duration.ofDays(7)));

        ReplayDocument poison1 = replay("poison-1", "db/replays/poison-1.modlreplay", 30);
        ReplayDocument poison2 = replay("poison-2", "db/replays/poison-2.modlreplay", 20);
        Date page1Max = poison2.getCreatedAt();
        ReplayDocument fresh = replay("fresh-1", "db/replays/fresh-1.modlreplay", 10);

        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.countExpiredWithMissingStorageKey(server, cutoff)).thenReturn(0L);
        when(replayRepository.findExpiredCompletedOrFailed(server, cutoff, 2))
            .thenReturn(List.of(poison1, poison2));
        when(replayRepository.findExpiredCompletedOrFailedAfter(eq(server), eq(cutoff), eq(page1Max), eq(2)))
            .thenReturn(List.of(fresh));
        when(replayDeletionService.deleteReplayWithStorage(server, poison1))
            .thenReturn(ReplayDeletionOutcome.STORAGE_DELETE_FAILED);
        when(replayDeletionService.deleteReplayWithStorage(server, poison2))
            .thenReturn(ReplayDeletionOutcome.STORAGE_DELETE_FAILED);
        when(replayDeletionService.deleteReplayWithStorage(server, fresh))
            .thenReturn(ReplayDeletionOutcome.DELETED);

        pagingService.runCleanupOnce();

        verify(replayDeletionService).deleteReplayWithStorage(server, poison1);
        verify(replayDeletionService).deleteReplayWithStorage(server, poison2);
        verify(replayDeletionService).deleteReplayWithStorage(server, fresh);
    }

    @Test
    void cleanupDrainsBacklogLargerThanBatchSize() {
        LegacyReplayCleanupService pagingService = pagingService(2);
        Date cutoff = Date.from(NOW.minus(Duration.ofDays(7)));

        ReplayDocument a = replay("a", "db/replays/a.modlreplay", 30);
        ReplayDocument b = replay("b", "db/replays/b.modlreplay", 25);
        Date page1Max = b.getCreatedAt();
        ReplayDocument c = replay("c", "db/replays/c.modlreplay", 20);
        ReplayDocument d = replay("d", "db/replays/d.modlreplay", 15);
        Date page2Max = d.getCreatedAt();

        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.countExpiredWithMissingStorageKey(server, cutoff)).thenReturn(0L);
        when(replayRepository.findExpiredCompletedOrFailed(server, cutoff, 2))
            .thenReturn(List.of(a, b));
        when(replayRepository.findExpiredCompletedOrFailedAfter(eq(server), eq(cutoff), eq(page1Max), eq(2)))
            .thenReturn(List.of(c, d));
        when(replayRepository.findExpiredCompletedOrFailedAfter(eq(server), eq(cutoff), eq(page2Max), eq(2)))
            .thenReturn(List.of());
        when(replayDeletionService.deleteReplayWithStorage(eq(server), any()))
            .thenReturn(ReplayDeletionOutcome.DELETED);

        pagingService.runCleanupOnce();

        verify(replayDeletionService).deleteReplayWithStorage(server, a);
        verify(replayDeletionService).deleteReplayWithStorage(server, b);
        verify(replayDeletionService).deleteReplayWithStorage(server, c);
        verify(replayDeletionService).deleteReplayWithStorage(server, d);
        verify(replayRepository).findExpiredCompletedOrFailedAfter(server, cutoff, page1Max, 2);
        verify(replayRepository).findExpiredCompletedOrFailedAfter(server, cutoff, page2Max, 2);
    }

    private LegacyReplayCleanupService pagingService(int batchSize) {
        LegacyReplayCleanupProperties properties = new LegacyReplayCleanupProperties();
        properties.setEnabled(true);
        properties.setBatchSize(batchSize);
        return new LegacyReplayCleanupService(
            properties,
            serverRepository,
            replayRepository,
            replayRetentionSettingsService,
            replayDeletionService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ReplayDocument replay(String id, String storageKey) {
        return replay(id, storageKey, 8);
    }

    private ReplayDocument replay(String id, String storageKey, int daysAgo) {
        ReplayDocument replay = new ReplayDocument();
        replay.setId(id);
        replay.setStorageKey(storageKey);
        replay.setStatus(ReplayDocument.STATUS_COMPLETE);
        replay.setCreatedAt(Date.from(NOW.minus(Duration.ofDays(daysAgo))));
        return replay;
    }
}
