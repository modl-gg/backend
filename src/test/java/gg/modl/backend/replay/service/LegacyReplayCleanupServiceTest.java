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
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.data.ReplayRetentionSettings;
import gg.modl.backend.settings.service.ReplayRetentionSettingsService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
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
    private S3StorageService s3StorageService;
    private StorageMetadataService storageMetadataService;
    private LegacyReplayCleanupService cleanupService;
    private Server server;

    @BeforeEach
    void setUp() {
        serverRepository = mock(ServerMongoRepository.class);
        replayRepository = mock(ReplayMongoRepository.class);
        replayRetentionSettingsService = mock(ReplayRetentionSettingsService.class);
        s3StorageService = mock(S3StorageService.class);
        storageMetadataService = mock(StorageMetadataService.class);
        LegacyReplayCleanupProperties properties = new LegacyReplayCleanupProperties();
        properties.setEnabled(true);
        properties.setBatchSize(100);
        cleanupService = new LegacyReplayCleanupService(
            properties,
            serverRepository,
            replayRepository,
            replayRetentionSettingsService,
            s3StorageService,
            storageMetadataService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    }

    @Test
    void cleanupDeletesExpiredReplayStorageMetadataAndRecord() {
        ReplayDocument replay = replay("replay-1", "db/replays/replay-1.modlreplay");
        Date cutoff = Date.from(NOW.minus(Duration.ofDays(7)));

        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.countExpiredWithMissingStorageKey(server, cutoff)).thenReturn(0L);
        when(replayRepository.findExpiredCompletedOrFailed(server, cutoff, 100)).thenReturn(List.of(replay));
        when(s3StorageService.deleteFile("db/replays/replay-1.modlreplay")).thenReturn(true);
        when(storageMetadataService.removeFile(server, "db/replays/replay-1.modlreplay")).thenReturn(true);
        when(replayRepository.deleteByReplayId(server, "replay-1")).thenReturn(true);

        cleanupService.runCleanupOnce();

        verify(s3StorageService).deleteFile("db/replays/replay-1.modlreplay");
        verify(storageMetadataService).removeFile(server, "db/replays/replay-1.modlreplay");
        verify(replayRepository).deleteByReplayId(server, "replay-1");
    }

    @Test
    void cleanupDoesNotRemoveMetadataOrReplayRecordWhenStorageDeleteFails() {
        ReplayDocument replay = replay("replay-1", "db/replays/replay-1.modlreplay");
        Date cutoff = Date.from(NOW.minus(Duration.ofDays(7)));

        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.countExpiredWithMissingStorageKey(server, cutoff)).thenReturn(0L);
        when(replayRepository.findExpiredCompletedOrFailed(server, cutoff, 100)).thenReturn(List.of(replay));
        when(s3StorageService.deleteFile("db/replays/replay-1.modlreplay")).thenReturn(false);

        cleanupService.runCleanupOnce();

        verify(storageMetadataService, never()).removeFile(server, "db/replays/replay-1.modlreplay");
        verify(replayRepository, never()).deleteByReplayId(server, "replay-1");
    }

    @Test
    void cleanupDoesNotRemoveReplayRecordWhenMetadataRemovalFails() {
        ReplayDocument replay = replay("replay-1", "db/replays/replay-1.modlreplay");
        Date cutoff = Date.from(NOW.minus(Duration.ofDays(7)));

        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.countExpiredWithMissingStorageKey(server, cutoff)).thenReturn(0L);
        when(replayRepository.findExpiredCompletedOrFailed(server, cutoff, 100)).thenReturn(List.of(replay));
        when(s3StorageService.deleteFile("db/replays/replay-1.modlreplay")).thenReturn(true);
        when(storageMetadataService.removeFile(server, "db/replays/replay-1.modlreplay")).thenReturn(false);

        cleanupService.runCleanupOnce();

        verify(storageMetadataService).removeFile(server, "db/replays/replay-1.modlreplay");
        verify(replayRepository, never()).deleteByReplayId(server, "replay-1");
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
        verify(s3StorageService, never()).deleteFile(any());
        verify(storageMetadataService, never()).removeFile(eq(server), any());
        verify(replayRepository, never()).deleteByReplayId(eq(server), any());
    }

    @Test
    void cleanupSkipsTenantWhenRetentionIsDisabled() {
        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(false, 7));

        cleanupService.runCleanupOnce();

        verify(replayRepository, never()).countExpiredWithMissingStorageKey(eq(server), any(Date.class));
        verify(replayRepository, never()).findExpiredCompletedOrFailed(
            eq(server),
            any(Date.class),
            anyInt()
        );
        verify(s3StorageService, never()).deleteFile(any());
        verify(storageMetadataService, never()).removeFile(
            eq(server),
            any()
        );
    }

    @Test
    void cleanupPagesPastPersistentlyFailingHeadObject() {
        LegacyReplayCleanupService pagingService = pagingService(2);
        Date cutoff = Date.from(NOW.minus(Duration.ofDays(7)));

        // First (full) page: two poison rows whose storage delete always fails.
        ReplayDocument poison1 = replay("poison-1", "db/replays/poison-1.modlreplay", 30);
        ReplayDocument poison2 = replay("poison-2", "db/replays/poison-2.modlreplay", 20);
        // The cursor advances to the page's MAX createdAt; poison2 (20 days ago) is newer than
        // poison1 (30 days ago), so it carries the larger createdAt.
        Date page1Max = poison2.getCreatedAt();
        // Second page: newer deletable rows reached only via the cursor.
        ReplayDocument fresh = replay("fresh-1", "db/replays/fresh-1.modlreplay", 10);

        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.countExpiredWithMissingStorageKey(server, cutoff)).thenReturn(0L);
        when(replayRepository.findExpiredCompletedOrFailed(server, cutoff, 2))
            .thenReturn(List.of(poison1, poison2));
        when(replayRepository.findExpiredCompletedOrFailedAfter(eq(server), eq(cutoff), eq(page1Max), eq(2)))
            .thenReturn(List.of(fresh));
        when(s3StorageService.deleteFile("db/replays/poison-1.modlreplay")).thenReturn(false);
        when(s3StorageService.deleteFile("db/replays/poison-2.modlreplay")).thenReturn(false);
        when(s3StorageService.deleteFile("db/replays/fresh-1.modlreplay")).thenReturn(true);
        when(storageMetadataService.removeFile(server, "db/replays/fresh-1.modlreplay")).thenReturn(true);
        when(replayRepository.deleteByReplayId(server, "fresh-1")).thenReturn(true);

        pagingService.runCleanupOnce();

        verify(replayRepository).deleteByReplayId(server, "fresh-1");
        verify(replayRepository, never()).deleteByReplayId(server, "poison-1");
        verify(replayRepository, never()).deleteByReplayId(server, "poison-2");
    }

    @Test
    void cleanupDrainsBacklogLargerThanBatchSize() {
        LegacyReplayCleanupService pagingService = pagingService(2);
        Date cutoff = Date.from(NOW.minus(Duration.ofDays(7)));

        ReplayDocument a = replay("a", "db/replays/a.modlreplay", 30);
        ReplayDocument b = replay("b", "db/replays/b.modlreplay", 25);
        // Cursor advances to the page's MAX createdAt: b (25 days ago) is newer than a (30 days ago).
        Date page1Max = b.getCreatedAt();
        ReplayDocument c = replay("c", "db/replays/c.modlreplay", 20);
        ReplayDocument d = replay("d", "db/replays/d.modlreplay", 15);
        // d (15 days ago) is newer than c (20 days ago), so it carries the page's max createdAt.
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
        when(s3StorageService.deleteFile(any())).thenReturn(true);
        when(storageMetadataService.removeFile(eq(server), any())).thenReturn(true);
        when(replayRepository.deleteByReplayId(eq(server), any())).thenReturn(true);

        pagingService.runCleanupOnce();

        verify(replayRepository).deleteByReplayId(server, "a");
        verify(replayRepository).deleteByReplayId(server, "b");
        verify(replayRepository).deleteByReplayId(server, "c");
        verify(replayRepository).deleteByReplayId(server, "d");
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
            s3StorageService,
            storageMetadataService,
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
