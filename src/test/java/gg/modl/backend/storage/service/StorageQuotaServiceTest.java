package gg.modl.backend.storage.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import org.junit.jupiter.api.Test;

class StorageQuotaServiceTest {

    @Test
    void canUploadUsesLiveS3UsageAndCustomPremiumLimit() {
        StorageMetadataService metadataService = mock(StorageMetadataService.class);
        S3StorageService s3StorageService = mock(S3StorageService.class);
        UsageTrackingService usageTrackingService = mock(UsageTrackingService.class);
        StorageQuotaService service = new StorageQuotaService(metadataService, s3StorageService, usageTrackingService);
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setMaxStorageLimitBytes(10_000L);
        when(s3StorageService.calculateStorageUsed(server)).thenReturn(9_000L);

        assertTrue(service.canUpload(server, 1_000L));
        assertFalse(service.canUpload(server, 1_001L));
    }

    @Test
    void canUploadFallsBackToDefaultPremiumLimit() {
        StorageQuotaService service = new StorageQuotaService(
            mock(StorageMetadataService.class),
            mock(S3StorageService.class),
            mock(UsageTrackingService.class)
        );
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);

        assertTrue(service.canUpload(server, 200L * 1024 * 1024 * 1024));
    }
}
