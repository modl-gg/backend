package gg.modl.backend.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.player.service.PunishmentEvidenceService;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationStatus;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.data.StorageFileDocument;
import gg.modl.backend.storage.dto.request.EvidenceConfirmUploadRequest;
import gg.modl.backend.storage.dto.request.EvidenceItemRequest;
import gg.modl.backend.storage.dto.request.EvidencePresignUploadRequest;
import gg.modl.backend.storage.dto.request.SubmitEvidenceRequest;
import gg.modl.backend.storage.dto.response.UploadResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvidenceUploadServiceTest {

    @Mock
    private EvidenceUploadTokenService tokenService;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private PlayerMongoRepository playerRepository;

    @Mock
    private ServerService serverService;

    @Mock
    private MediaValidationService validationService;

    @Mock
    private PunishmentEvidenceService punishmentEvidenceService;

    @Mock
    private StorageMetadataService storageMetadataService;

    @Mock
    private UploadOrchestrationService uploadOrchestrationService;

    private EvidenceUploadService evidenceUploadService;

    @BeforeEach
    void setUp() {
        evidenceUploadService = new EvidenceUploadService(
            tokenService,
            s3StorageService,
            playerRepository,
            serverService,
            validationService,
            punishmentEvidenceService,
            storageMetadataService,
            uploadOrchestrationService
        );
    }

    private EvidenceUploadTokenService.UploadToken uploadToken() {
        return new EvidenceUploadTokenService.UploadToken(
            "token-1",
            "db",
            "PUN-1",
            "player-1",
            "Moderator",
            Instant.now()
        );
    }

    @Test
    void submitEvidenceDelegatesPunishmentMutationAndInvalidatesToken() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        when(tokenService.validateToken("token-1")).thenReturn(uploadToken());
        when(s3StorageService.getCdnDomain()).thenReturn("cdn.example.com");
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(storageMetadataService.findConfirmedFiles(eq(server), any()))
            .thenReturn(Map.of("db/evidence/PUN-1/file.png", new StorageFileDocument("db/evidence/PUN-1/file.png", "file.png", 42L, "image/png", "evidence")));
        when(punishmentEvidenceService.addUploadedEvidence(eq(server), eq("PUN-1"), eq("Moderator"), any(), any()))
            .thenReturn(new PunishmentOperationResult(
                PunishmentOperationStatus.SUCCESS,
                "ok",
                true,
                1
            ));

        EvidenceUploadService.SubmitEvidenceResult result = evidenceUploadService.submitEvidence(
            "token-1",
            new SubmitEvidenceRequest(List.of(
                new EvidenceItemRequest(
                    "https://cdn.example.com/db/evidence/PUN-1/file.png",
                    "file.png",
                    "image/png",
                    42L
                )
            ))
        );

        assertEquals(EvidenceUploadService.SubmitEvidenceStatus.SUCCESS, result.status());
        verify(punishmentEvidenceService).addUploadedEvidence(eq(server), eq("PUN-1"), eq("Moderator"), any(), any());
        verify(tokenService).invalidateToken("token-1");
    }

    @Test
    void confirmUploadMapsQuotaExceededOutcomeFromOrchestration() {
        String key = "db/evidence/PUN-1/file.png";
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        when(tokenService.validateToken("token-1")).thenReturn(uploadToken());
        when(validationService.isKeyOwnedByServer(key, "db")).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(uploadOrchestrationService.confirm(server, key, false))
            .thenReturn(new UploadOrchestrationService.ConfirmOutcome(
                UploadOrchestrationService.ConfirmStatus.QUOTA_EXCEEDED, null));

        EvidenceUploadService.ConfirmUploadResult result = evidenceUploadService.confirmUpload(
            "token-1",
            new EvidenceConfirmUploadRequest(key)
        );

        assertEquals(EvidenceUploadService.ConfirmUploadStatus.QUOTA_EXCEEDED, result.status());
    }

    @Test
    void confirmUploadReturnsSuccessWithDetailsFromOrchestration() {
        String key = "db/evidence/PUN-1/file.png";
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        UploadResponse details = new UploadResponse(key, "https://cdn.example.com/" + key, "file.png", 42L, "image/png");

        when(tokenService.validateToken("token-1")).thenReturn(uploadToken());
        when(validationService.isKeyOwnedByServer(key, "db")).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(uploadOrchestrationService.confirm(server, key, false))
            .thenReturn(new UploadOrchestrationService.ConfirmOutcome(
                UploadOrchestrationService.ConfirmStatus.SUCCESS, details));

        EvidenceUploadService.ConfirmUploadResult result = evidenceUploadService.confirmUpload(
            "token-1",
            new EvidenceConfirmUploadRequest(key)
        );

        assertEquals(EvidenceUploadService.ConfirmUploadStatus.SUCCESS, result.status());
        assertEquals(details, result.upload());
    }

    @Test
    void confirmUploadRejectsKeyOutsidePunishmentScopeWithoutOrchestrating() {
        String key = "db/evidence/OTHER-PUN/file.png";

        when(tokenService.validateToken("token-1")).thenReturn(uploadToken());
        when(validationService.isKeyOwnedByServer(key, "db")).thenReturn(true);

        EvidenceUploadService.ConfirmUploadResult result = evidenceUploadService.confirmUpload(
            "token-1",
            new EvidenceConfirmUploadRequest(key)
        );

        assertEquals(EvidenceUploadService.ConfirmUploadStatus.INVALID_KEY, result.status());
        verify(uploadOrchestrationService, never()).confirm(any(), any(), anyBoolean());
    }

    @Test
    void presignUploadMapsQuotaExceededOutcomeFromOrchestration() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        when(tokenService.validateToken("token-1")).thenReturn(uploadToken());
        when(s3StorageService.isConfigured()).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(uploadOrchestrationService.presign(eq(server), any()))
            .thenReturn(new UploadOrchestrationService.PresignOutcome(
                UploadOrchestrationService.PresignStatus.QUOTA_EXCEEDED, "Storage quota exceeded", null));

        EvidenceUploadService.PresignUploadResult result = evidenceUploadService.presignUpload(
            "token-1",
            new EvidencePresignUploadRequest("file.png", "image/png", 42L)
        );

        assertEquals(EvidenceUploadService.PresignUploadStatus.QUOTA_EXCEEDED, result.status());
    }
}
