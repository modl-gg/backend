package gg.modl.backend.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.validation.BeanValidationRunner;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageQuotaService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReplayServiceTest {

    @Mock
    private ReplayMongoRepository replayRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private StorageQuotaService storageQuotaService;

    @Mock
    private TrainingDataService trainingDataService;

    private ReplayService replayService;
    private Server server;
    private static Validator validator;

    @BeforeEach
    void setUp() {
        if (validator == null) {
            validator = Validation.buildDefaultValidatorFactory().getValidator();
        }
        replayService = new ReplayService(
            replayRepository,
            s3StorageService,
            storageQuotaService,
            trainingDataService,
            new BeanValidationRunner(validator)
        );
        ReflectionTestUtils.setField(replayService, "maxFileSize", 10 * 1024 * 1024L);
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    }

    @Test
    void initUploadRejectsFilesThatExceedStorageQuotaWithValidationException() {
        when(storageQuotaService.canUpload(server, 1024L)).thenReturn(false);

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> replayService.initUpload(server, "1.21.4", 1024L, null, null)
        );

        assertEquals("Storage quota exceeded", exception.getMessage());
        verifyNoInteractions(s3StorageService, replayRepository, trainingDataService);
    }

    @Test
    void initUploadRejectsFilesThatExceedConfiguredMaxSizeWithValidationException() {
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> replayService.initUpload(server, "1.21.4", (10 * 1024 * 1024L) + 1, null, null)
        );

        assertEquals("File size exceeds maximum of 10 MB", exception.getMessage());
        verifyNoInteractions(storageQuotaService, s3StorageService, replayRepository, trainingDataService);
    }

    @Test
    void initUploadPersistsTargetMetadata() {
        when(storageQuotaService.canUpload(server, 2048L)).thenReturn(true);
        when(s3StorageService.createPresignedUploadUrl(server, "replays", "replay.modlreplay", "application/octet-stream", 2048L))
            .thenReturn(new PresignUploadResponse(
                "https://upload.example/replay",
                "db/replays/replay.modlreplay",
                Instant.parse("2026-05-18T12:00:00Z"),
                "PUT",
                Map.of("Content-Type", "application/octet-stream")
            ));

        replayService.initUpload(server, "1.21.4", 2048L, " 3f8c9c5a-6b6e-4f2c-9b7f-1a2b3c4d5e6f ", " TargetName ");

        ArgumentCaptor<ReplayDocument> replayCaptor = ArgumentCaptor.forClass(ReplayDocument.class);
        verify(replayRepository).saveEntity(eq(server), replayCaptor.capture());
        ReplayDocument saved = replayCaptor.getValue();
        assertEquals("3f8c9c5a-6b6e-4f2c-9b7f-1a2b3c4d5e6f", saved.getTargetUuid());
        assertEquals("TargetName", saved.getTargetName());
        assertEquals(ReplayDocument.STATUS_PENDING, saved.getStatus());
    }

    @Test
    void initUploadRejectsInvalidTargetUuid() {
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> replayService.initUpload(server, "1.21.4", 2048L, "not-a-uuid", "TargetName")
        );

        assertEquals("Target UUID must be a valid UUID", exception.getMessage());
        verifyNoInteractions(s3StorageService, replayRepository, trainingDataService);
    }

    @Test
    void initUploadRejectsLongTargetName() {
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> replayService.initUpload(server, "1.21.4", 2048L, "3f8c9c5a-6b6e-4f2c-9b7f-1a2b3c4d5e6f", "x".repeat(17))
        );

        assertEquals("Target name is too long", exception.getMessage());
        verifyNoInteractions(s3StorageService, replayRepository, trainingDataService);
    }
}
