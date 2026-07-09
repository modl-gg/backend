package gg.modl.backend.storage.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.StorageFileMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.data.StorageFileDocument;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StorageMetadataServiceTest {
    private static final String KEY = "db/replays/r1.modlreplay";
    private static final String SERVER_ID = "srv-1";

    private StorageFileMongoRepository storageFileRepository;
    private ServerMongoRepository serverRepository;
    private S3StorageService s3StorageService;
    private StorageSyncService storageSyncService;
    private StorageMetadataService service;
    private Server server;

    @BeforeEach
    void setUp() {
        storageFileRepository = mock(StorageFileMongoRepository.class);
        serverRepository = mock(ServerMongoRepository.class);
        s3StorageService = mock(S3StorageService.class);
        storageSyncService = mock(StorageSyncService.class);
        service = new StorageMetadataService(storageFileRepository, serverRepository, s3StorageService, storageSyncService);
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        server.setId(SERVER_ID);
    }

    @Test
    void removeFileDecrementsUsageByRemovedDocumentSize() {
        when(storageFileRepository.findAndRemoveByKey(server, KEY)).thenReturn(Optional.of(file(KEY, 100L)));

        assertTrue(service.removeFile(server, KEY));

        verify(serverRepository).decrementStorageUsed(SERVER_ID, 100L);
    }

    @Test
    void removeFileDecrementsUsageOnlyOnceAcrossRepeatedRemovals() {
        when(storageFileRepository.findAndRemoveByKey(server, KEY))
            .thenReturn(Optional.of(file(KEY, 100L)))
            .thenReturn(Optional.empty());

        assertTrue(service.removeFile(server, KEY));
        assertTrue(service.removeFile(server, KEY));

        verify(serverRepository, times(1)).decrementStorageUsed(SERVER_ID, 100L);
    }

    @Test
    void removeFileDoesNotDecrementUsageWhenFileIsAbsent() {
        when(storageFileRepository.findAndRemoveByKey(server, KEY)).thenReturn(Optional.empty());

        assertTrue(service.removeFile(server, KEY));

        verify(serverRepository, never()).decrementStorageUsed(anyString(), anyLong());
    }

    @Test
    void removeFileReturnsFalseAndDoesNotDecrementWhenRepositoryThrows() {
        when(storageFileRepository.findAndRemoveByKey(server, KEY)).thenThrow(new RuntimeException("mongo unavailable"));

        assertFalse(service.removeFile(server, KEY));

        verify(serverRepository, never()).decrementStorageUsed(anyString(), anyLong());
    }

    @Test
    void removeFilesDecrementsUsageBySummedSizeOfRemovedDocumentsOnce() {
        List<String> keys = List.of("db/replays/a.modlreplay", "db/replays/b.modlreplay");
        when(storageFileRepository.findAndRemoveByKeys(server, keys))
            .thenReturn(List.of(file("db/replays/a.modlreplay", 100L), file("db/replays/b.modlreplay", 50L)));

        service.removeFiles(server, keys);

        verify(serverRepository).decrementStorageUsed(SERVER_ID, 150L);
    }

    @Test
    void removeFilesDoesNotDecrementWhenNoDocumentsWereRemoved() {
        List<String> keys = List.of("db/replays/a.modlreplay");
        when(storageFileRepository.findAndRemoveByKeys(server, keys)).thenReturn(List.of());

        service.removeFiles(server, keys);

        verify(serverRepository, never()).decrementStorageUsed(anyString(), anyLong());
    }

    private StorageFileDocument file(String key, long size) {
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        return new StorageFileDocument(key, fileName, size, "application/octet-stream", "replay");
    }
}
