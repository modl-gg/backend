package gg.modl.backend.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import gg.modl.backend.storage.service.StorageQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private StorageMetadataService storageMetadataService;

    private ReplayService replayService;
    private Server server;

    @BeforeEach
    void setUp() {
        replayService = new ReplayService(
            replayRepository,
            s3StorageService,
            storageQuotaService,
            trainingDataService,
            storageMetadataService
        );
        ReflectionTestUtils.setField(replayService, "maxFileSize", 10 * 1024 * 1024L);
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    }

    @Test
    void initUploadRejectsFilesThatExceedStorageQuotaWithValidationException() {
        when(storageQuotaService.canUpload(server, 1024L)).thenReturn(false);

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> replayService.initUpload(server, "1.21.4", 1024L)
        );

        assertEquals("Storage quota exceeded", exception.getMessage());
        verifyNoInteractions(s3StorageService, replayRepository, trainingDataService, storageMetadataService);
    }

    @Test
    void initUploadRejectsFilesThatExceedConfiguredMaxSizeWithValidationException() {
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> replayService.initUpload(server, "1.21.4", (10 * 1024 * 1024L) + 1)
        );

        assertEquals("File size exceeds maximum of 10 MB", exception.getMessage());
        verifyNoInteractions(storageQuotaService, s3StorageService, replayRepository, trainingDataService, storageMetadataService);
    }
}
