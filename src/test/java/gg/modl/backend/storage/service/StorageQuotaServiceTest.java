package gg.modl.backend.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.limits.DefaultServerLimitPolicy;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import org.junit.jupiter.api.Test;

class StorageQuotaServiceTest {

    @Test
    void canUploadUsesTrackedCounterAndDoesNotTriggerSync() {
        StorageMetadataService metadataService = mock(StorageMetadataService.class);
        UsageTrackingService usageTrackingService = mock(UsageTrackingService.class);
        StorageSyncService storageSyncService = mock(StorageSyncService.class);
        StorageQuotaService service = new StorageQuotaService(
            metadataService,
            usageTrackingService,
            mock(ServerMongoRepository.class),
            storageSyncService,
            new DefaultServerLimitPolicy()
        );
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setMaxStorageLimitBytes(10_000L);
        server.setStorageUsedBytes(9_000L);

        assertTrue(service.canUpload(server, 1_000L));
        assertFalse(service.canUpload(server, 1_001L));
        verify(storageSyncService, never()).triggerAsyncSync(any(Server.class));
    }

    @Test
    void canUploadFallsBackToDefaultPremiumLimit() {
        StorageQuotaService service = new StorageQuotaService(
            mock(StorageMetadataService.class),
            mock(UsageTrackingService.class),
            mock(ServerMongoRepository.class),
            mock(StorageSyncService.class),
            new DefaultServerLimitPolicy()
        );
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setStorageUsedBytes(0L);

        assertTrue(service.canUpload(server, 200L * 1024 * 1024 * 1024));
    }

    @Test
    void canUploadTreatsMissingCounterAsZeroAndTriggersSync() {
        StorageSyncService storageSyncService = mock(StorageSyncService.class);
        StorageQuotaService service = new StorageQuotaService(
            mock(StorageMetadataService.class),
            mock(UsageTrackingService.class),
            mock(ServerMongoRepository.class),
            storageSyncService,
            new DefaultServerLimitPolicy()
        );
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);

        assertTrue(service.canUpload(server, 1_000L));
        verify(storageSyncService).triggerAsyncSync(server);
    }

    @Test
    void confirmAndRecordFileReservesQuotaBeforeRecordingMetadata() {
        StorageMetadataService metadataService = mock(StorageMetadataService.class);
        UsageTrackingService usageTrackingService = mock(UsageTrackingService.class);
        ServerMongoRepository serverRepository = mock(ServerMongoRepository.class);
        StorageQuotaService service = new StorageQuotaService(
            metadataService,
            usageTrackingService,
            serverRepository,
            mock(StorageSyncService.class),
            new DefaultServerLimitPolicy()
        );
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-id");
        server.setMaxStorageLimitBytes(10_000L);
        server.setStorageUsedBytes(9_000L);
        when(serverRepository.tryIncrementStorageUsedWithinLimit("server-id", 1_000L, 10_000L)).thenReturn(true);
        when(metadataService.recordReservedFile(server, "db/evidence/file.png", 1_000L, "image/png"))
            .thenReturn(StorageMetadataService.RecordFileResult.INSERTED);

        assertEquals(StorageQuotaService.ConfirmResult.SUCCESS,
            service.confirmAndRecordFile(server, "db/evidence/file.png", 1_000L, "image/png"));
        verify(metadataService).recordReservedFile(server, "db/evidence/file.png", 1_000L, "image/png");
    }

    @Test
    void confirmAndRecordFileRejectsWhenAtomicReservationFails() {
        StorageMetadataService metadataService = mock(StorageMetadataService.class);
        UsageTrackingService usageTrackingService = mock(UsageTrackingService.class);
        ServerMongoRepository serverRepository = mock(ServerMongoRepository.class);
        StorageQuotaService service = new StorageQuotaService(
            metadataService,
            usageTrackingService,
            serverRepository,
            mock(StorageSyncService.class),
            new DefaultServerLimitPolicy()
        );
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-id");
        server.setMaxStorageLimitBytes(10_000L);
        server.setStorageUsedBytes(9_500L);
        when(serverRepository.tryIncrementStorageUsedWithinLimit("server-id", 1_000L, 10_000L)).thenReturn(false);

        assertEquals(StorageQuotaService.ConfirmResult.QUOTA_EXCEEDED,
            service.confirmAndRecordFile(server, "db/evidence/file.png", 1_000L, "image/png"));
    }

    @Test
    void confirmAndRecordFileRollsBackReservationWhenMetadataAlreadyExistsAfterRace() {
        StorageMetadataService metadataService = mock(StorageMetadataService.class);
        UsageTrackingService usageTrackingService = mock(UsageTrackingService.class);
        ServerMongoRepository serverRepository = mock(ServerMongoRepository.class);
        StorageQuotaService service = new StorageQuotaService(
            metadataService,
            usageTrackingService,
            serverRepository,
            mock(StorageSyncService.class),
            new DefaultServerLimitPolicy()
        );
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-id");
        server.setMaxStorageLimitBytes(10_000L);
        server.setStorageUsedBytes(9_000L);
        when(serverRepository.tryIncrementStorageUsedWithinLimit("server-id", 1_000L, 10_000L)).thenReturn(true);
        when(metadataService.recordReservedFile(server, "db/evidence/file.png", 1_000L, "image/png"))
            .thenReturn(StorageMetadataService.RecordFileResult.ALREADY_EXISTS);

        assertEquals(StorageQuotaService.ConfirmResult.SUCCESS,
            service.confirmAndRecordFile(server, "db/evidence/file.png", 1_000L, "image/png"));
        verify(serverRepository).decrementStorageUsed("server-id", 1_000L);
    }
}
