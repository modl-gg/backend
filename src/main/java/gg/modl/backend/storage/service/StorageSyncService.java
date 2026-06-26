package gg.modl.backend.storage.service;

import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.StorageFileMongoRepository;
import gg.modl.backend.server.data.Server;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageSyncService {
    private final S3StorageService s3StorageService;
    private final StorageFileMongoRepository storageFileRepository;
    private final ServerMongoRepository serverRepository;

    private final Set<String> syncsInProgress = ConcurrentHashMap.newKeySet();

    public void triggerAsyncSync(Server server) {
        String serverId = server.getId();
        if (!syncsInProgress.add(serverId)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                syncServerFiles(server, false);
            } catch (Exception e) {
                log.warn("Async sync failed for server {}", server.getDatabaseName(), e);
            } finally {
                syncsInProgress.remove(serverId);
            }
        });
    }

    public int syncServerFiles(Server server, boolean authoritative) {
        if (!s3StorageService.isConfigured()) {
            // S3 unconfigured: listAllObjects would return an empty list, which would otherwise wipe
            // the tenant's storage_files metadata and zero its usage. Abort without touching state.
            log.warn("Skipping storage sync for server {}: S3 is not configured", server.getDatabaseName());
            return 0;
        }

        List<S3StorageService.S3ObjectInfo> objects = s3StorageService.listAllObjects(server);

        int inserted = 0;
        long totalSize = 0;
        for (S3StorageService.S3ObjectInfo obj : objects) {
            String fileName = obj.key().substring(obj.key().lastIndexOf("/") + 1);
            String category = S3StorageService.categorizeFile(obj.key());

            Query query = Query.query(Criteria.where("key").is(obj.key()));
            Update update = new Update()
                .setOnInsert("key", obj.key())
                .setOnInsert("fileName", fileName)
                .set("size", obj.size())
                .setOnInsert("contentType", "application/octet-stream")
                .setOnInsert("category", category)
                .setOnInsert("createdAt", Date.from(obj.lastModified()));

            UpdateResult result = storageFileRepository.upsert(server, query, update);
            if (result.getUpsertedId() != null) {
                inserted++;
            }
            totalSize += obj.size();
        }

        List<String> s3Keys = new ArrayList<>(objects.size());
        for (S3StorageService.S3ObjectInfo obj : objects) {
            s3Keys.add(obj.key());
        }
        storageFileRepository.deleteByKeyNotIn(server, s3Keys);

        if (authoritative) {
            serverRepository.setStorageUsed(server.getId(), totalSize);
        } else {
            // Background/opportunistic sync must never LOWER the counter: a concurrent confirm may have
            // reserved quota for an object not yet visible in this S3 listing. Raise-only CAS.
            serverRepository.setStorageUsedIfBelow(server.getId(), totalSize);
        }

        log.info("Synced {} files ({} new) for server {}", objects.size(), inserted, server.getDatabaseName());
        return objects.size();
    }
}
