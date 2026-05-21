package gg.modl.backend.storage.service;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.StorageFileMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.data.StorageFileDocument;
import gg.modl.backend.storage.dto.response.StorageFileResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageMetadataService {
    private final StorageFileMongoRepository storageFileRepository;
    private final ServerMongoRepository serverRepository;
    private final S3StorageService s3StorageService;
    private final StorageSyncService storageSyncService;

    public boolean hasFile(Server server, String key) {
        return storageFileRepository.findByKey(server, key).isPresent();
    }

    public RecordFileResult recordFile(Server server, String key, long size, String contentType) {
        return recordFile(server, key, size, contentType, true);
    }

    public RecordFileResult recordReservedFile(Server server, String key, long size, String contentType) {
        return recordFile(server, key, size, contentType, false);
    }

    private RecordFileResult recordFile(Server server, String key, long size, String contentType, boolean updateUsage) {
        try {
            if (storageFileRepository.findByKey(server, key).isPresent()) {
                return RecordFileResult.ALREADY_EXISTS;
            }

            String fileName = key.substring(key.lastIndexOf("/") + 1);
            String category = S3StorageService.categorizeFile(key);
            StorageFileDocument doc = new StorageFileDocument(key, fileName, size, contentType, category);
            storageFileRepository.saveEntity(server, doc);
            if (updateUsage) {
                serverRepository.incrementStorageUsed(server.getId(), size);
            }
            return RecordFileResult.INSERTED;
        } catch (DuplicateKeyException e) {
            return RecordFileResult.ALREADY_EXISTS;
        } catch (Exception e) {
            log.warn("Failed to record file metadata for key: {}. Sync can recover this.", key, e);
            return RecordFileResult.FAILED;
        }
    }

    public enum RecordFileResult {
        INSERTED,
        ALREADY_EXISTS,
        FAILED
    }

    public boolean removeFile(Server server, String key) {
        try {
            storageFileRepository.findByKey(server, key).ifPresent(doc -> {
                storageFileRepository.deleteByKey(server, key);
                serverRepository.decrementStorageUsed(server.getId(), doc.getSize());
            });
            return true;
        } catch (Exception e) {
            log.warn("Failed to remove file metadata for key: {}", key, e);
            return false;
        }
    }

    public void removeFiles(Server server, List<String> keys) {
        try {
            List<StorageFileDocument> docs = storageFileRepository.findByKeys(server, keys);
            long totalSize = docs.stream().mapToLong(StorageFileDocument::getSize).sum();
            storageFileRepository.deleteByKeys(server, keys);
            if (totalSize > 0) {
                serverRepository.decrementStorageUsed(server.getId(), totalSize);
            }
        } catch (Exception e) {
            log.warn("Failed to remove file metadata for bulk delete", e);
        }
    }

    public Map<String, Long> calculateStorageByType(Server server) {
        if (!isServerSynced(server)) {
            storageSyncService.triggerAsyncSync(server);
            return s3StorageService.calculateStorageByType(server);
        }
        return storageFileRepository.aggregateStorageByCategory(server);
    }

    public long getStorageUsedBytes(Server server) {
        if (!isServerSynced(server)) {
            return s3StorageService.calculateStorageUsed(server);
        }
        return server.getStorageUsedBytes();
    }

    public List<StorageFileResponse> listFiles(Server server, String prefix) {
        if (!isServerSynced(server)) {
            storageSyncService.triggerAsyncSync(server);
            return s3StorageService.listFiles(server, prefix);
        }

        String fullPrefix = server.getDatabaseName() + "/" + (prefix != null ? prefix : "");
        List<StorageFileDocument> docs = storageFileRepository.findByKeyPrefix(server, fullPrefix, 1000);

        return docs.stream()
            .map(doc -> new StorageFileResponse(
                doc.getKey(),
                doc.getFileName(),
                doc.getSize(),
                doc.getContentType() != null ? doc.getContentType() : "application/octet-stream",
                doc.getCreatedAt(),
                s3StorageService.getCdnUrl(doc.getKey())
            ))
            .toList();
    }

    private boolean isServerSynced(Server server) {
        return server.getStorageUsedBytes() != null;
    }
}
