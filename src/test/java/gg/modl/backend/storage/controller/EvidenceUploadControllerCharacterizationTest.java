package gg.modl.backend.storage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.player.service.PunishmentEvidenceService;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationStatus;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.data.StorageFileDocument;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import gg.modl.backend.storage.service.EvidenceUploadService;
import gg.modl.backend.storage.service.EvidenceUploadTokenService;
import gg.modl.backend.storage.service.MediaValidationService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import gg.modl.backend.storage.service.UploadOrchestrationService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EvidenceUploadControllerCharacterizationTest {

    private static final String KEY = "db/evidence/PUN-1/file.png";
    private static final String PRESIGN_BODY =
        "{\"fileName\":\"test.png\",\"contentType\":\"image/png\",\"fileSize\":1024}";
    private static final String CONFIRM_BODY = "{\"key\":\"" + KEY + "\"}";
    private static final String SUBMIT_BODY =
        "{\"evidence\":[{\"url\":\"https://cdn.example.com/" + KEY
            + "\",\"fileName\":\"file.png\",\"fileType\":\"image/png\",\"fileSize\":1024}]}";

    private EvidenceUploadTokenService tokenService;
    private S3StorageService s3StorageService;
    private PlayerMongoRepository playerRepository;
    private ServerService serverService;
    private MediaValidationService validationService;
    private PunishmentEvidenceService punishmentEvidenceService;
    private StorageMetadataService storageMetadataService;
    private UploadOrchestrationService uploadOrchestrationService;
    private MockMvc mockMvc;
    private Server server;

    @BeforeEach
    void setUp() {
        tokenService = mock(EvidenceUploadTokenService.class);
        s3StorageService = mock(S3StorageService.class);
        playerRepository = mock(PlayerMongoRepository.class);
        serverService = mock(ServerService.class);
        validationService = mock(MediaValidationService.class);
        punishmentEvidenceService = mock(PunishmentEvidenceService.class);
        storageMetadataService = mock(StorageMetadataService.class);
        uploadOrchestrationService = mock(UploadOrchestrationService.class);
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        EvidenceUploadService service = new EvidenceUploadService(
            tokenService,
            s3StorageService,
            playerRepository,
            serverService,
            validationService,
            punishmentEvidenceService,
            storageMetadataService,
            uploadOrchestrationService
        );

        mockMvc = MockMvcBuilders
            .standaloneSetup(new EvidenceUploadController(service))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
    }

    private EvidenceUploadTokenService.UploadToken validToken() {
        return new EvidenceUploadTokenService.UploadToken(
            "token-1", "db", "PUN-1", "player-1", "Moderator", Instant.now());
    }

    private String base() {
        return RESTMappingV1.PUBLIC_EVIDENCE_UPLOAD;
    }

    @Test
    void validateTokenInvalid() throws Exception {
        when(tokenService.validateToken("bad")).thenReturn(null);

        mockMvc.perform(get(base() + "/bad"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Invalid or expired upload token"));
    }

    @Test
    void validateTokenValid() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(playerRepository.findByMinecraftUuid(any(Server.class), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get(base() + "/token-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.punishmentId").value("PUN-1"))
            .andExpect(jsonPath("$.playerName").value("Unknown"))
            .andExpect(jsonPath("$.issuerName").value("Moderator"));
    }

    @Test
    void presignInvalidToken() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(null);

        perform("/token-1/presign", PRESIGN_BODY)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Invalid or expired upload token"));
    }

    @Test
    void presignStorageNotConfigured() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(s3StorageService.isConfigured()).thenReturn(false);

        perform("/token-1/presign", PRESIGN_BODY)
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("File storage is not configured"));
    }

    @Test
    void presignServerNotFound() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(s3StorageService.isConfigured()).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(null);

        perform("/token-1/presign", PRESIGN_BODY)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Server not found"));
    }

    @Test
    void presignQuotaExceeded() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(s3StorageService.isConfigured()).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(uploadOrchestrationService.presign(eq(server), any()))
            .thenReturn(new UploadOrchestrationService.PresignOutcome(
                UploadOrchestrationService.PresignStatus.QUOTA_EXCEEDED, "Storage quota exceeded", null));

        perform("/token-1/presign", PRESIGN_BODY)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Storage quota exceeded. Please contact the server administrator."));
    }

    @Test
    void presignValidationFailed() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(s3StorageService.isConfigured()).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(uploadOrchestrationService.presign(eq(server), any()))
            .thenReturn(new UploadOrchestrationService.PresignOutcome(
                UploadOrchestrationService.PresignStatus.VALIDATION_FAILED, "File too large", null));

        perform("/token-1/presign", PRESIGN_BODY)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("File too large"));
    }

    @Test
    void presignSuccess() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(s3StorageService.isConfigured()).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(uploadOrchestrationService.presign(eq(server), any()))
            .thenReturn(new UploadOrchestrationService.PresignOutcome(
                UploadOrchestrationService.PresignStatus.SUCCESS, null,
                new PresignUploadResponse(
                    "https://upload.example.com",
                    KEY,
                    Instant.parse("2030-01-01T00:00:00Z"),
                    "PUT",
                    Map.of("x-amz-acl", "private"))));

        perform("/token-1/presign", PRESIGN_BODY)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.presignedUrl").value("https://upload.example.com"))
            .andExpect(jsonPath("$.key").value(KEY))
            .andExpect(jsonPath("$.expiresAt").value("2030-01-01T00:00:00Z"))
            .andExpect(jsonPath("$.method").value("PUT"))
            .andExpect(jsonPath("$.requiredHeaders['x-amz-acl']").value("private"));
    }

    @Test
    void confirmInvalidToken() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(null);

        perform("/token-1/confirm", CONFIRM_BODY)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Invalid or expired upload token"));
    }

    @Test
    void confirmInvalidKeyNotOwned() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(validationService.isKeyOwnedByServer(KEY, "db")).thenReturn(false);

        perform("/token-1/confirm", CONFIRM_BODY)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.message").value("Upload key does not belong to this evidence token"));
    }

    @Test
    void confirmInvalidKeyPrefixMismatch() throws Exception {
        String foreignKey = "db/evidence/OTHER-PUN/file.png";
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(validationService.isKeyOwnedByServer(foreignKey, "db")).thenReturn(true);

        perform("/token-1/confirm", "{\"key\":\"" + foreignKey + "\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.message").value("Upload key does not belong to this evidence token"));
    }

    @Test
    void confirmServerMissingUploadNotFound() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(validationService.isKeyOwnedByServer(KEY, "db")).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(null);
        when(s3StorageService.getUploadDetails(KEY)).thenReturn(null);

        perform("/token-1/confirm", CONFIRM_BODY)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Upload not found. File may not have been uploaded yet."));
    }

    @Test
    void confirmServerMissingSuccess() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(validationService.isKeyOwnedByServer(KEY, "db")).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(null);
        when(s3StorageService.getUploadDetails(KEY))
            .thenReturn(new UploadResponse(KEY, "https://cdn.example.com/" + KEY, "file.png", 42L, "image/png"));

        perform("/token-1/confirm", CONFIRM_BODY)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.key").value(KEY))
            .andExpect(jsonPath("$.url").value("https://cdn.example.com/" + KEY))
            .andExpect(jsonPath("$.fileName").value("file.png"))
            .andExpect(jsonPath("$.size").value(42))
            .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void confirmOrchestratedSuccess() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(validationService.isKeyOwnedByServer(KEY, "db")).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(uploadOrchestrationService.confirm(server, KEY, false))
            .thenReturn(new UploadOrchestrationService.ConfirmOutcome(
                UploadOrchestrationService.ConfirmStatus.SUCCESS,
                new UploadResponse(KEY, "https://cdn.example.com/" + KEY, "file.png", 42L, "image/png")));

        perform("/token-1/confirm", CONFIRM_BODY)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.key").value(KEY))
            .andExpect(jsonPath("$.url").value("https://cdn.example.com/" + KEY))
            .andExpect(jsonPath("$.fileName").value("file.png"))
            .andExpect(jsonPath("$.size").value(42))
            .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void confirmOrchestratedUploadNotFound() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(validationService.isKeyOwnedByServer(KEY, "db")).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(uploadOrchestrationService.confirm(server, KEY, false))
            .thenReturn(new UploadOrchestrationService.ConfirmOutcome(
                UploadOrchestrationService.ConfirmStatus.UPLOAD_NOT_FOUND, null));

        perform("/token-1/confirm", CONFIRM_BODY)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Upload not found. File may not have been uploaded yet."));
    }

    @Test
    void confirmOrchestratedQuotaExceeded() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(validationService.isKeyOwnedByServer(KEY, "db")).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(uploadOrchestrationService.confirm(server, KEY, false))
            .thenReturn(new UploadOrchestrationService.ConfirmOutcome(
                UploadOrchestrationService.ConfirmStatus.QUOTA_EXCEEDED, null));

        perform("/token-1/confirm", CONFIRM_BODY)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Storage quota exceeded"));
    }

    @Test
    void confirmOrchestratedRecordFailed() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(validationService.isKeyOwnedByServer(KEY, "db")).thenReturn(true);
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(uploadOrchestrationService.confirm(server, KEY, false))
            .thenReturn(new UploadOrchestrationService.ConfirmOutcome(
                UploadOrchestrationService.ConfirmStatus.RECORD_FAILED, null));

        perform("/token-1/confirm", CONFIRM_BODY)
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value("Failed to record upload"));
    }

    @Test
    void submitInvalidToken() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(null);

        perform("/token-1/submit", SUBMIT_BODY)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Invalid or expired upload token"));
    }

    @Test
    void submitServerNotFound() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(serverService.getServerByDatabaseName("db")).thenReturn(null);

        perform("/token-1/submit", SUBMIT_BODY)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Server not found"));
    }

    @Test
    void submitInvalidUrl() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(s3StorageService.getCdnDomain()).thenReturn("cdn.other.com");

        perform("/token-1/submit", SUBMIT_BODY)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Invalid evidence URL"));
    }

    @Test
    void submitPunishmentNotFound() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(s3StorageService.getCdnDomain()).thenReturn("cdn.example.com");
        when(storageMetadataService.findConfirmedFiles(eq(server), any()))
            .thenReturn(Map.of(KEY, new StorageFileDocument(KEY, "file.png", 42L, "image/png", "evidence")));
        when(punishmentEvidenceService.addUploadedEvidence(eq(server), eq("PUN-1"), eq("Moderator"), any(), any()))
            .thenReturn(new PunishmentOperationResult(PunishmentOperationStatus.NOT_FOUND, "Punishment not found", false, 0));

        perform("/token-1/submit", SUBMIT_BODY)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Punishment not found"));
    }

    @Test
    void submitSuccess() throws Exception {
        when(tokenService.validateToken("token-1")).thenReturn(validToken());
        when(serverService.getServerByDatabaseName("db")).thenReturn(server);
        when(s3StorageService.getCdnDomain()).thenReturn("cdn.example.com");
        when(storageMetadataService.findConfirmedFiles(eq(server), any()))
            .thenReturn(Map.of(KEY, new StorageFileDocument(KEY, "file.png", 42L, "image/png", "evidence")));
        when(punishmentEvidenceService.addUploadedEvidence(eq(server), eq("PUN-1"), eq("Moderator"), any(), any()))
            .thenReturn(new PunishmentOperationResult(PunishmentOperationStatus.SUCCESS, "ok", true, 1));

        perform("/token-1/submit", SUBMIT_BODY)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.message").value("Evidence uploaded successfully"));
    }

    private ResultActions perform(String path, String body) throws Exception {
        return mockMvc.perform(post(base() + path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }
}
