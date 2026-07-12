package gg.modl.backend.migration.service;

import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.migration.dto.UpdateProgressRequest;
import gg.modl.backend.migration.service.MigrationService.FileSizeError;
import gg.modl.backend.server.data.Server;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MigrationUploadService {
    private final MigrationService migrationService;
    private final MigrationProcessor migrationProcessor;

    public UploadResult beginUpload(Server server, MultipartFile file) {
        if (file.isEmpty()) {
            throw new ValidationException("No file uploaded");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".json")) {
            throw new ValidationException("Only JSON files are allowed");
        }

        Optional<FileSizeError> sizeError = migrationService.validateFileSize(server, file);
        if (sizeError.isPresent()) {
            return new UploadResult.FileTooLarge(sizeError.get());
        }

        migrationService.requireActiveMigrationForUpload(server);

        Path filePath = migrationService.saveUploadedFile(file);
        try {
            migrationService.updateProgress(server, new UpdateProgressRequest(
                "uploading_json",
                "Migration file uploaded successfully. Starting data processing...",
                0, 0, null
            ));
            migrationProcessor.processFileAsync(server, filePath);
        } catch (TaskRejectedException e) {
            migrationService.discardUpload(server, filePath,
                "Migration processing is busy. Please try again shortly.");
            return new UploadResult.ProcessingBusy();
        } catch (RuntimeException e) {
            migrationService.discardUpload(server, filePath, "Migration failed to start.");
            return new UploadResult.StartFailed();
        }

        return new UploadResult.Success(file.getSize());
    }

    public sealed interface UploadResult {
        record Success(long fileSize) implements UploadResult {}

        record FileTooLarge(FileSizeError error) implements UploadResult {}

        record ProcessingBusy() implements UploadResult {}

        record StartFailed() implements UploadResult {}
    }
}
