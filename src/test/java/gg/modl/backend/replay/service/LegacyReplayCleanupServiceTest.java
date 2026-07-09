package gg.modl.backend.replay.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.ReplayMongoRepository.ReplayCursor;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.infrastructure.scheduling.SchedulerLeaseService;
import gg.modl.backend.replay.config.LegacyReplayCleanupProperties;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.service.ReplayDeletionService.ReplayBatchDeletionResult;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacyReplayCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-18T12:00:00Z");

    private ServerMongoRepository serverRepository;
    private ReplayMongoRepository replayRepository;
    private TicketMongoRepository ticketRepository;
    private ReplayRetentionSettingsService replayRetentionSettingsService;
    private ReplayDeletionService replayDeletionService;
    private SchedulerLeaseService schedulerLeaseService;
    private LegacyReplayCleanupService cleanupService;
    private Server server;

    @BeforeEach
    void setUp() {
        serverRepository = mock(ServerMongoRepository.class);
        replayRepository = mock(ReplayMongoRepository.class);
        ticketRepository = mock(TicketMongoRepository.class);
        replayRetentionSettingsService = mock(ReplayRetentionSettingsService.class);
        replayDeletionService = mock(ReplayDeletionService.class);
        schedulerLeaseService = mock(SchedulerLeaseService.class);
        when(replayDeletionService.deleteReplaysWithStorage(any(Server.class), anyList()))
            .thenReturn(new ReplayBatchDeletionResult(0, 0L));
        cleanupService = cleanupService(100);
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    }

    @Test
    void cleanupDelegatesExpiredReplayDeletionToReplayDeletionService() {
        ReplayDocument replay = replay("replay-1", "db/replays/replay-1.modlreplay", 8);
        Date cutoff = cutoff(7);

        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.findExpiredWithStorageKey(server, cutoff, null, 100)).thenReturn(List.of(replay));

        cleanupService.runCleanupOnce();

        verify(replayRepository).findExpiredWithStorageKey(server, cutoff, null, 100);
        verify(replayRepository).findExpiredWithMissingStorageKey(server, cutoff, null, 100);
        verify(replayDeletionService).deleteReplaysWithStorage(server, List.of(replay));
    }

    @Test
    void cleanupDrainsBacklogLargerThanBatchSizeUsingKeysetCursor() {
        cleanupService = cleanupService(2);
        Date cutoff = cutoff(7);

        ReplayDocument a = replay("a", "db/replays/a.modlreplay", 30);
        ReplayDocument b = replay("b", "db/replays/b.modlreplay", 25);
        ReplayDocument c = replay("c", "db/replays/c.modlreplay", 20);
        ReplayDocument d = replay("d", "db/replays/d.modlreplay", 15);
        ReplayCursor afterB = new ReplayCursor(b.getCreatedAt(), "b");
        ReplayCursor afterD = new ReplayCursor(d.getCreatedAt(), "d");

        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.findExpiredWithStorageKey(server, cutoff, null, 2)).thenReturn(List.of(a, b));
        when(replayRepository.findExpiredWithStorageKey(server, cutoff, afterB, 2)).thenReturn(List.of(c, d));

        cleanupService.runCleanupOnce();

        verify(replayRepository).findExpiredWithStorageKey(server, cutoff, null, 2);
        verify(replayRepository).findExpiredWithStorageKey(server, cutoff, afterB, 2);
        verify(replayRepository).findExpiredWithStorageKey(server, cutoff, afterD, 2);
        verify(replayDeletionService).deleteReplaysWithStorage(server, List.of(a, b));
        verify(replayDeletionService).deleteReplaysWithStorage(server, List.of(c, d));
    }

    @Test
    void cleanupExcludesReplaysReferencedByUnresolvedTickets() {
        ReplayDocument a = replay("a", "db/replays/a.modlreplay", 30);
        ReplayDocument b = replay("b", "db/replays/b.modlreplay", 25);
        Date cutoff = cutoff(7);

        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.findExpiredWithStorageKey(server, cutoff, null, 100)).thenReturn(List.of(a, b));
        when(ticketRepository.findReplayIdsReferencedByUnresolvedTicket(server, List.of("a", "b")))
            .thenReturn(Set.of("a"));

        cleanupService.runCleanupOnce();

        verify(replayDeletionService).deleteReplaysWithStorage(server, List.of(b));
        verify(replayDeletionService, never()).deleteReplaysWithStorage(server, List.of(a, b));
    }

    @Test
    void cleanupUsesEachTenantOwnCutoffAndNeverCrossesTenants() {
        Server serverA = new Server("serverA", "domainA", "dbA", "a@example.com", true, ServerPlan.FREE);
        Server serverB = new Server("serverB", "domainB", "dbB", "b@example.com", true, ServerPlan.FREE);
        Date cutoffA = cutoff(7);
        Date cutoffB = cutoff(14);
        ReplayDocument replayA = replay("ra", "dbA/replays/ra.modlreplay", 30);
        ReplayDocument replayB = replay("rb", "dbB/replays/rb.modlreplay", 30);

        when(serverRepository.findAll()).thenReturn(List.of(serverA, serverB));
        when(replayRetentionSettingsService.getReplayRetentionSettings(serverA))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRetentionSettingsService.getReplayRetentionSettings(serverB))
            .thenReturn(new ReplayRetentionSettings(true, 14));
        when(replayRepository.findExpiredWithStorageKey(serverA, cutoffA, null, 100)).thenReturn(List.of(replayA));
        when(replayRepository.findExpiredWithStorageKey(serverB, cutoffB, null, 100)).thenReturn(List.of(replayB));

        cleanupService.runCleanupOnce();

        verify(replayRepository).findExpiredWithStorageKey(serverA, cutoffA, null, 100);
        verify(replayRepository).findExpiredWithStorageKey(serverB, cutoffB, null, 100);
        verify(replayRepository, never()).findExpiredWithStorageKey(eq(serverA), eq(cutoffB), any(), anyInt());
        verify(replayRepository, never()).findExpiredWithStorageKey(eq(serverB), eq(cutoffA), any(), anyInt());
        verify(replayDeletionService).deleteReplaysWithStorage(serverA, List.of(replayA));
        verify(replayDeletionService).deleteReplaysWithStorage(serverB, List.of(replayB));
        verify(replayDeletionService, never()).deleteReplaysWithStorage(serverB, List.of(replayA));
        verify(replayDeletionService, never()).deleteReplaysWithStorage(serverA, List.of(replayB));
    }

    @Test
    void cleanupSkipsDisabledTenantWhileSweepingEnabledTenantInSameRun() {
        Server disabledServer = new Server("disabled", "disabledDomain", "dbDisabled", "d@example.com", true, ServerPlan.FREE);
        Date cutoff = cutoff(7);
        ReplayDocument replay = replay("replay-1", "db/replays/replay-1.modlreplay", 8);

        when(serverRepository.findAll()).thenReturn(List.of(disabledServer, server));
        when(replayRetentionSettingsService.getReplayRetentionSettings(disabledServer))
            .thenReturn(new ReplayRetentionSettings(false, 7));
        when(replayRetentionSettingsService.getReplayRetentionSettings(server))
            .thenReturn(new ReplayRetentionSettings(true, 7));
        when(replayRepository.findExpiredWithStorageKey(server, cutoff, null, 100)).thenReturn(List.of(replay));

        cleanupService.runCleanupOnce();

        verify(replayRepository, never()).findExpiredWithStorageKey(eq(disabledServer), any(), any(), anyInt());
        verify(replayRepository, never()).findExpiredWithMissingStorageKey(eq(disabledServer), any(), any(), anyInt());
        verify(replayDeletionService, never()).deleteReplaysWithStorage(eq(disabledServer), anyList());
        verify(replayRepository).findExpiredWithStorageKey(server, cutoff, null, 100);
        verify(replayDeletionService).deleteReplaysWithStorage(server, List.of(replay));
    }

    private LegacyReplayCleanupService cleanupService(int batchSize) {
        LegacyReplayCleanupProperties properties = new LegacyReplayCleanupProperties();
        properties.setEnabled(true);
        properties.setBatchSize(batchSize);
        return new LegacyReplayCleanupService(
            properties,
            serverRepository,
            replayRepository,
            ticketRepository,
            replayRetentionSettingsService,
            replayDeletionService,
            schedulerLeaseService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static Date cutoff(int days) {
        return Date.from(NOW.minus(Duration.ofDays(days)));
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
