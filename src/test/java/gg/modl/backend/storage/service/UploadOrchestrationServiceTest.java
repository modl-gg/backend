package gg.modl.backend.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.limits.ServerLimitPolicy;
import gg.modl.backend.limits.ServerLimits;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UploadOrchestrationServiceTest {

    @Mock
    private MediaValidationService validationService;

    @Mock
    private StorageQuotaService quotaService;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private StorageMetadataService storageMetadataService;

    @Mock
    private ServerLimitPolicy serverLimitPolicy;

    @Mock
    private TempUploadReclamationService reclamationService;

    private UploadOrchestrationService uploadOrchestrationService;

    @BeforeEach
    void setUp() {
        uploadOrchestrationService = new UploadOrchestrationService(
            validationService,
            quotaService,
            s3StorageService,
            storageMetadataService,
            serverLimitPolicy,
            reclamationService
        );
    }

    private Server server() {
        return new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
    }

    private UploadOrchestrationService.UploadPresignRequest presignRequest() {
        return new UploadOrchestrationService.UploadPresignRequest(
            "evidence",
            "file.png",
            "image/png",
            42L,
            "PUN-1",
            true,
            false
        );
    }

    @Test
    void presignSucceedsWhenValidationLimitsAndQuotaPass() {
        Server server = server();
        ServerLimits limits = ServerLimits.builder().maxUploadBytes(Long.MAX_VALUE).maxStorageBytes(Long.MAX_VALUE).build();
        PresignUploadResponse presigned = new PresignUploadResponse(
            "https://uploads.example/replay", "db/evidence/PUN-1/file.png",
            Instant.parse("2026-05-15T12:00:00Z"), "PUT", Map.of("Content-Type", "image/png"));

        when(validationService.validateMetadata("file.png", "image/png", 42L, "evidence", true))
            .thenReturn(new MediaValidationService.ValidationResult(true, null));
        when(serverLimitPolicy.resolve(server)).thenReturn(limits);
        when(quotaService.canUpload(server, 42L)).thenReturn(true);
        when(s3StorageService.createPresignedUploadUrl(server, "evidence", "file.png", "image/png", 42L, "PUN-1"))
            .thenReturn(presigned);

        UploadOrchestrationService.PresignOutcome outcome = uploadOrchestrationService.presign(server, presignRequest());

        assertEquals(UploadOrchestrationService.PresignStatus.SUCCESS, outcome.status());
        assertEquals(presigned, outcome.upload());
    }

    @Test
    void presignRejectsWhenMetadataValidationFails() {
        Server server = server();
        when(validationService.validateMetadata("file.png", "image/png", 42L, "evidence", true))
            .thenReturn(new MediaValidationService.ValidationResult(false, "File type not allowed"));

        UploadOrchestrationService.PresignOutcome outcome = uploadOrchestrationService.presign(server, presignRequest());

        assertEquals(UploadOrchestrationService.PresignStatus.VALIDATION_FAILED, outcome.status());
        assertEquals("File type not allowed", outcome.message());
        verify(s3StorageService, never()).createPresignedUploadUrl(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void presignRejectsWhenStorageQuotaExceeded() {
        Server server = server();
        ServerLimits limits = ServerLimits.builder().maxUploadBytes(Long.MAX_VALUE).maxStorageBytes(Long.MAX_VALUE).build();
        when(validationService.validateMetadata("file.png", "image/png", 42L, "evidence", true))
            .thenReturn(new MediaValidationService.ValidationResult(true, null));
        when(serverLimitPolicy.resolve(server)).thenReturn(limits);
        when(quotaService.canUpload(server, 42L)).thenReturn(false);

        UploadOrchestrationService.PresignOutcome outcome = uploadOrchestrationService.presign(server, presignRequest());

        assertEquals(UploadOrchestrationService.PresignStatus.QUOTA_EXCEEDED, outcome.status());
        verify(s3StorageService, never()).createPresignedUploadUrl(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void confirmReturnsSuccessWhenQuotaReserved() {
        Server server = server();
        String key = "db/evidence/PUN-1/file.png";
        UploadResponse details = new UploadResponse(key, "https://cdn.example.com/" + key, "file.png", 42L, "image/png");

        when(s3StorageService.getUploadDetails(key)).thenReturn(details);
        when(quotaService.confirmAndRecordFile(server, key, 42L, "image/png"))
            .thenReturn(StorageQuotaService.ConfirmResult.SUCCESS);

        UploadOrchestrationService.ConfirmOutcome outcome = uploadOrchestrationService.confirm(server, key, false);

        assertEquals(UploadOrchestrationService.ConfirmStatus.SUCCESS, outcome.status());
        assertEquals(details, outcome.upload());
        verify(storageMetadataService, never()).cleanupOrphanedUpload(any(), any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void confirmReturnsUploadNotFoundWhenObjectMissing() {
        Server server = server();
        String key = "db/evidence/PUN-1/missing.png";
        when(s3StorageService.getUploadDetails(key)).thenReturn(null);

        UploadOrchestrationService.ConfirmOutcome outcome = uploadOrchestrationService.confirm(server, key, false);

        assertEquals(UploadOrchestrationService.ConfirmStatus.UPLOAD_NOT_FOUND, outcome.status());
        verify(quotaService, never()).confirmAndRecordFile(any(), any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void confirmCleansUpOrphanAndReportsQuotaExceeded() {
        Server server = server();
        String key = "db/evidence/PUN-1/file.png";
        UploadResponse details = new UploadResponse(key, "https://cdn.example.com/" + key, "file.png", 42L, "image/png");

        when(s3StorageService.getUploadDetails(key)).thenReturn(details);
        when(quotaService.confirmAndRecordFile(server, key, 42L, "image/png"))
            .thenReturn(StorageQuotaService.ConfirmResult.QUOTA_EXCEEDED);

        UploadOrchestrationService.ConfirmOutcome outcome = uploadOrchestrationService.confirm(server, key, false);

        assertEquals(UploadOrchestrationService.ConfirmStatus.QUOTA_EXCEEDED, outcome.status());
        verify(storageMetadataService).cleanupOrphanedUpload(server, key, 42L, "image/png");
    }
}
