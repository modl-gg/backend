package gg.modl.backend.storage.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.modl.backend.database.mongo.repository.StorageFileMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.config.StorageExecutorConfig;
import gg.modl.backend.storage.data.StorageFileDocument;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TempUploadReclamationService {
    private static final int MAX_TRACKED_SERVERS = 50_000;
    private static final int LOOKUP_CHUNK_SIZE = 500;

    private final S3StorageService s3StorageService;
    private final StorageFileMongoRepository storageFileRepository;
    private final Duration orphanTtl;
    private final Cache<String, Boolean> recentlyReclaimed;

    public TempUploadReclamationService(
        S3StorageService s3StorageService,
        StorageFileMongoRepository storageFileRepository,
        @Value("${modl.storage.temp-upload.orphan-ttl:PT6H}") Duration orphanTtl,
        @Value("${modl.storage.temp-upload.reclaim-interval:PT12H}") Duration reclaimInterval
    ) {
        this.s3StorageService = s3StorageService;
        this.storageFileRepository = storageFileRepository;
        this.orphanTtl = orphanTtl;
        this.recentlyReclaimed = Caffeine.newBuilder()
            .expireAfterWrite(reclaimInterval)
            .maximumSize(MAX_TRACKED_SERVERS)
            .build();
    }

    @Async(StorageExecutorConfig.STORAGE_TASK_EXECUTOR)
    public void reclaimAsync(Server server) {
        if (!s3StorageService.isConfigured()) {
            return;
        }
        if (recentlyReclaimed.getIfPresent(server.getId()) != null) {
            return;
        }
        recentlyReclaimed.put(server.getId(), Boolean.TRUE);

        try {
            reclaim(server);
        } catch (Exception e) {
            log.warn("Temp upload reclamation failed for server {}", server.getDatabaseName(), e);
        }
    }

    private void reclaim(Server server) {
        Instant cutoff = Instant.now().minus(orphanTtl);
        int reclaimed = 0;

        for (String prefix : TempUploadKeys.prefixes(server)) {
            List<String> staleKeys = s3StorageService.listObjectInfosByPrefix(prefix).stream()
                .filter(object -> object.lastModified().isBefore(cutoff))
                .map(S3StorageService.S3ObjectInfo::key)
                .toList();

            for (int start = 0; start < staleKeys.size(); start += LOOKUP_CHUNK_SIZE) {
                List<String> chunk = staleKeys.subList(start, Math.min(start + LOOKUP_CHUNK_SIZE, staleKeys.size()));
                Set<String> confirmedKeys = storageFileRepository.findByKeys(server, chunk).stream()
                    .map(StorageFileDocument::getKey)
                    .collect(Collectors.toSet());

                List<String> unconfirmedOrphans = chunk.stream()
                    .filter(key -> !confirmedKeys.contains(key))
                    .toList();
                if (!unconfirmedOrphans.isEmpty()) {
                    reclaimed += s3StorageService.bulkDelete(unconfirmedOrphans);
                }
            }
        }

        if (reclaimed > 0) {
            log.info("Reclaimed {} orphaned temp upload object(s) for server {}", reclaimed, server.getDatabaseName());
        }
    }
}
