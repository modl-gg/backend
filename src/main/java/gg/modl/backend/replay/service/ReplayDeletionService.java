package gg.modl.backend.replay.service;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
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

    public enum ReplayDeletionOutcome {
        DELETED,
        STORAGE_DELETE_FAILED,
        METADATA_REMOVE_FAILED,
        ALREADY_ABSENT
    }

    public ReplayDeletionOutcome deleteReplayWithStorage(Server server, ReplayDocument replay) {
        if (!s3StorageService.deleteFile(replay.getStorageKey())) {
            return ReplayDeletionOutcome.STORAGE_DELETE_FAILED;
        }
        if (!storageMetadataService.removeFile(server, replay.getStorageKey())) {
            return ReplayDeletionOutcome.METADATA_REMOVE_FAILED;
        }
        boolean removed = replayRepository.deleteByReplayId(server, replay.getId());
        ticketRepository.clearReplayReferences(server, List.of(replay.getId()));
        return removed ? ReplayDeletionOutcome.DELETED : ReplayDeletionOutcome.ALREADY_ABSENT;
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
            long removedRecords = replayRepository.deleteByReplayIds(server, replayIds);
            long clearedTicketReferences = ticketRepository.clearReplayReferences(server, replayIds);

            log.info(
                "Reconciled deleted replay storage server={} matchedKeys={} removedRecords={} clearedTicketReferences={}",
                server.getDatabaseName(),
                replayKeys.size(),
                removedRecords,
                clearedTicketReferences
            );
            return (int) removedRecords;
        } catch (Exception e) {
            log.warn("Failed to reconcile deleted replay storage for server {}", server.getDatabaseName(), e);
            return 0;
        }
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
}
