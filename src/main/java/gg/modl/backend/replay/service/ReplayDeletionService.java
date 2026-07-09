package gg.modl.backend.replay.service;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.database.mongo.repository.TrainingSegmentRepository;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplayDeletionService {
    private static final String REPLAY_STORAGE_CATEGORY = "replay";

    private final ReplayMongoRepository replayRepository;
    private final S3StorageService s3StorageService;
    private final StorageMetadataService storageMetadataService;
    private final TicketMongoRepository ticketRepository;
    private final TrainingSegmentRepository trainingSegmentRepository;

    public ReplayBatchDeletionResult deleteReplaysWithStorage(Server server, List<ReplayDocument> replays) {
        if (replays == null || replays.isEmpty()) {
            return ReplayBatchDeletionResult.empty();
        }

        List<String> replayIds = new ArrayList<>(replays.size());
        List<String> storageKeys = new ArrayList<>(replays.size());
        for (ReplayDocument replay : replays) {
            if (replay.getId() == null) {
                continue;
            }
            replayIds.add(replay.getId());
            String storageKey = replay.getStorageKey();
            if (storageKey != null && !storageKey.isBlank()) {
                storageKeys.add(storageKey);
            }
        }
        if (replayIds.isEmpty()) {
            return ReplayBatchDeletionResult.empty();
        }

        if (!storageKeys.isEmpty()) {
            s3StorageService.bulkDelete(storageKeys);
            storageMetadataService.removeFiles(server, storageKeys);
        }
        long removedRecords = purgeReplayRecords(server, replayIds);
        return new ReplayBatchDeletionResult(replayIds.size(), removedRecords);
    }

    public int reconcileDeletedStorageKeys(Server server, Collection<String> deletedKeys) {
        List<String> replayKeys = replayStorageKeys(deletedKeys);
        if (replayKeys.isEmpty()) {
            return 0;
        }

        try {
            List<ReplayDocument> orphaned = replayRepository.findByStorageKeys(server, replayKeys);
            if (orphaned.isEmpty()) {
                return 0;
            }

            List<String> replayIds = orphaned.stream().map(ReplayDocument::getId).toList();
            long removedRecords = purgeReplayRecords(server, replayIds);

            log.info(
                "Reconciled deleted replay storage server={} matchedKeys={} removedRecords={}",
                server.getDatabaseName(),
                replayKeys.size(),
                removedRecords
            );
            return (int) removedRecords;
        } catch (Exception e) {
            log.warn("Failed to reconcile deleted replay storage for server {}", server.getDatabaseName(), e);
            return 0;
        }
    }

    private long purgeReplayRecords(Server server, List<String> replayIds) {
        long removedRecords = replayRepository.deleteByReplayIds(server, replayIds);
        trainingSegmentRepository.deleteByReplayIds(server.getDatabaseName(), replayIds);
        ticketRepository.clearReplayReferences(server, replayIds);
        return removedRecords;
    }

    private List<String> replayStorageKeys(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> replayKeys = new ArrayList<>();
        for (String key : keys) {
            if (key != null && REPLAY_STORAGE_CATEGORY.equals(S3StorageService.categorizeFile(key))) {
                replayKeys.add(key);
            }
        }
        return replayKeys;
    }

    public record ReplayBatchDeletionResult(int requested, long deleted) {
        static ReplayBatchDeletionResult empty() {
            return new ReplayBatchDeletionResult(0, 0L);
        }

        public long alreadyAbsent() {
            return Math.max(0L, requested - deleted);
        }
    }
}
