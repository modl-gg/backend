package gg.modl.backend.replay.service;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.ReplayMongoRepository.ReplayCursor;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.infrastructure.scheduling.SchedulerLeaseService;
import gg.modl.backend.replay.config.LegacyReplayCleanupProperties;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.service.ReplayDeletionService.ReplayBatchDeletionResult;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.ReplayRetentionSettings;
import gg.modl.backend.settings.service.ReplayRetentionSettingsService;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyReplayCleanupService {
    private static final int MAX_PAGES_PER_RUN = 1000;
    private static final String CLEANUP_LEASE = "legacy-replay-cleanup";

    private final LegacyReplayCleanupProperties properties;
    private final ServerMongoRepository serverRepository;
    private final ReplayMongoRepository replayRepository;
    private final TicketMongoRepository ticketRepository;
    private final ReplayRetentionSettingsService replayRetentionSettingsService;
    private final ReplayDeletionService replayDeletionService;
    private final SchedulerLeaseService schedulerLeaseService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "#{@legacyReplayCleanupProperties.intervalMs}")
    public void runScheduledCleanup() {
        if (!schedulerLeaseService.tryAcquire(CLEANUP_LEASE, properties.getClaimTtl())) {
            log.debug("Skipping legacy replay cleanup; lease held by another instance");
            return;
        }
        try {
            runCleanupOnce();
        } finally {
            schedulerLeaseService.release(CLEANUP_LEASE);
        }
    }

    public void runCleanupOnce() {
        if (!properties.isEnabled()) {
            return;
        }

        RunAggregate aggregate = new RunAggregate();
        List<Server> servers = serverRepository.findAll();
        for (Server server : servers) {
            try {
                ServerCleanupResult result = processServer(server);
                aggregate.fold(result);
                emitPerServerWarning(server, result);
            } catch (Exception e) {
                aggregate.failed++;
                log.error("Legacy replay cleanup failed for server {} - skipping",
                    server != null ? server.getId() : "null", e);
            }
        }

        log.info(
            "Legacy replay cleanup servers={} scanned={} deleted={} deletedMissingStorageKey={} skippedTicketReferenced={} alreadyAbsent={} skippedRetentionDisabled={} failedServers={}",
            servers.size(),
            aggregate.scanned,
            aggregate.deleted,
            aggregate.deletedMissingStorageKey,
            aggregate.skippedTicketReferenced,
            aggregate.alreadyAbsent,
            aggregate.skippedRetentionDisabled,
            aggregate.failed
        );
    }

    private ServerCleanupResult processServer(Server server) {
        ServerCleanupResult result = new ServerCleanupResult();
        if (server == null || server.getDatabaseName() == null || server.getDatabaseName().isBlank()) {
            return result;
        }

        ReplayRetentionSettings settings = replayRetentionSettingsService.getReplayRetentionSettings(server);
        if (!settings.isEnabled()) {
            result.retentionDisabled = true;
            return result;
        }

        Date cutoff = Date.from(clock.instant().minus(Duration.ofDays(settings.getDays())));
        int pageSize = Math.min(Math.max(properties.getBatchSize(), 1), 500);

        result.deleted = drain(server, cutoff, pageSize, replayRepository::findExpiredWithStorageKey, result);
        result.deletedMissingStorageKey =
            drain(server, cutoff, pageSize, replayRepository::findExpiredWithMissingStorageKey, result);
        return result;
    }

    private long drain(Server server, Date cutoff, int pageSize, ReplayPageFinder finder, ServerCleanupResult result) {
        long deletedTotal = 0;
        ReplayCursor cursor = null;
        int pages = 0;
        while (pages++ < MAX_PAGES_PER_RUN) {
            List<ReplayDocument> page = finder.find(server, cutoff, cursor, pageSize);
            if (page.isEmpty()) {
                break;
            }
            result.scanned += page.size();
            List<ReplayDocument> deletable = selectDeletable(server, page, result);
            ReplayBatchDeletionResult deletion = replayDeletionService.deleteReplaysWithStorage(server, deletable);
            deletedTotal += deletion.deleted();
            result.alreadyAbsent += deletion.alreadyAbsent();
            cursor = cursorFrom(page);
            if (page.size() < pageSize) {
                break;
            }
        }
        return deletedTotal;
    }

    private List<ReplayDocument> selectDeletable(Server server, List<ReplayDocument> page, ServerCleanupResult result) {
        List<String> pageIds = page.stream().map(ReplayDocument::getId).filter(Objects::nonNull).toList();
        Set<String> referenced = ticketRepository.findReplayIdsReferencedByUnresolvedTicket(server, pageIds);
        if (referenced.isEmpty()) {
            return page;
        }
        List<ReplayDocument> deletable = new ArrayList<>(page.size());
        for (ReplayDocument replay : page) {
            if (referenced.contains(replay.getId())) {
                result.skippedTicketReferenced++;
            } else {
                deletable.add(replay);
            }
        }
        return deletable;
    }

    private ReplayCursor cursorFrom(List<ReplayDocument> page) {
        ReplayDocument last = page.get(page.size() - 1);
        return new ReplayCursor(last.getCreatedAt(), last.getId());
    }

    private void emitPerServerWarning(Server server, ServerCleanupResult result) {
        if (result.deletedMissingStorageKey > 0) {
            log.warn(
                "Legacy replay cleanup deleted {} expired replays with missing storageKey server={}; storage sync will reconcile any orphaned objects",
                result.deletedMissingStorageKey,
                server.getId()
            );
        }
    }

    @FunctionalInterface
    private interface ReplayPageFinder {
        List<ReplayDocument> find(Server server, Date cutoff, ReplayCursor after, int limit);
    }

    private static final class ServerCleanupResult {
        private long scanned;
        private long deleted;
        private long deletedMissingStorageKey;
        private long skippedTicketReferenced;
        private long alreadyAbsent;
        private boolean retentionDisabled;
    }

    private static final class RunAggregate {
        private long scanned;
        private long deleted;
        private long deletedMissingStorageKey;
        private long skippedTicketReferenced;
        private long alreadyAbsent;
        private long skippedRetentionDisabled;
        private long failed;

        private void fold(ServerCleanupResult result) {
            scanned += result.scanned;
            deleted += result.deleted;
            deletedMissingStorageKey += result.deletedMissingStorageKey;
            skippedTicketReferenced += result.skippedTicketReferenced;
            alreadyAbsent += result.alreadyAbsent;
            if (result.retentionDisabled) {
                skippedRetentionDisabled++;
            }
        }
    }
}
