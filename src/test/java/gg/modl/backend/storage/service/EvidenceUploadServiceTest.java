package gg.modl.backend.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.player.service.PunishmentEvidenceService;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationStatus;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.service.StorageMetadataService;
import gg.modl.backend.storage.dto.request.EvidenceItemRequest;
import gg.modl.backend.storage.dto.request.SubmitEvidenceRequest;
import java.time.Instant;
import java.util.List;
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
    private StorageQuotaService quotaService;

    @Mock
    private MediaValidationService validationService;

    @Mock
    private PunishmentEvidenceService punishmentEvidenceService;

    @Mock
    private StorageMetadataService storageMetadataService;

    private EvidenceUploadService evidenceUploadService;

    @BeforeEach
    void setUp() {
        evidenceUploadService = new EvidenceUploadService(
            tokenService,
            s3StorageService,
            playerRepository,
            serverService,
            quotaService,
            validationService,
            punishmentEvidenceService,
            storageMetadataService
        );
    }

    @Test
    void submitEvidenceDelegatesPunishmentMutationAndInvalidatesToken() {
        EvidenceUploadTokenService.UploadToken uploadToken = new EvidenceUploadTokenService.UploadToken(
            "token-1",
            "db",
            "PUN-1",
            "player-1",
            "Moderator",
            Instant.now()
        );
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        when(tokenService.validateToken("token-1")).thenReturn(uploadToken);
        when(s3StorageService.getCdnDomain()).thenReturn("cdn.example.com");
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
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
}