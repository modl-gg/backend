package gg.modl.backend.migration.controller;

import gg.modl.backend.migration.dto.UpdateProgressRequest;
import gg.modl.backend.migration.service.MigrationService;
import gg.modl.backend.migration.service.MigrationService.FileSizeError;
import gg.modl.backend.migration.service.MigrationUploadService;
import gg.modl.backend.migration.service.MigrationUploadService.UploadResult;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_MIGRATION)
@RequiredArgsConstructor
public class MinecraftMigrationController {
    private final MigrationService migrationService;
    private final MigrationUploadService migrationUploadService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMigrationFile(
        @RequestParam("migrationFile") MultipartFile file,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        UploadResult result = migrationUploadService.beginUpload(server, file);
        return switch (result) {
            case UploadResult.Success success -> {
                Map<String, Object> body = Map.of(
                    "success", true,
                    "message", "Migration file uploaded successfully. Processing started.",
                    "fileSize", success.fileSize());
                yield ResponseEntity.ok(body);
            }
            case UploadResult.FileTooLarge fileTooLarge -> {
                FileSizeError error = fileTooLarge.error();
                Map<String, Object> body = Map.of(
                    "error", error.error(),
                    "message", error.message(),
                    "fileSize", error.fileSize(),
                    "limit", error.limit());
                yield ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
            }
            case UploadResult.ProcessingBusy ignored -> {
                Map<String, Object> body = Map.of(
                    "error", "Migration processing is busy",
                    "message", "The server is processing other migrations. Please try again shortly.");
                yield ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
            }
            case UploadResult.StartFailed ignored -> {
                Map<String, Object> body = Map.of(
                    "error", "Migration failed to start",
                    "message", "The migration could not be started. Please try again.");
                yield ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
            }
        };
    }

    @PostMapping("/progress")
    public ResponseEntity<?> updateProgress(
        @RequestBody @Valid UpdateProgressRequest progressRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        migrationService.updateProgress(server, progressRequest);

        return ResponseEntity.ok(Map.of("success", true));
    }
}
