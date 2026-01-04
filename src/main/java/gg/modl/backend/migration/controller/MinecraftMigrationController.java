package gg.modl.backend.migration.controller;

import gg.modl.backend.migration.dto.UpdateProgressRequest;
import gg.modl.backend.migration.service.MigrationProcessor;
import gg.modl.backend.migration.service.MigrationService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_MIGRATION)
@RequiredArgsConstructor
@Slf4j
public class MinecraftMigrationController {
    private final MigrationService migrationService;
    private final MigrationProcessor migrationProcessor;

    @Value("${modl.migration.upload-dir:uploads/migrations}")
    private String uploadDir;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMigrationFile(
            @RequestParam("migrationFile") MultipartFile file,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".json")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only JSON files are allowed"));
        }

        long fileSizeLimit = migrationService.getFileSizeLimit(server);
        if (file.getSize() > fileSizeLimit) {
            double fileSizeMB = file.getSize() / (1024.0 * 1024.0);
            double limitMB = fileSizeLimit / (1024.0 * 1024.0);

            migrationService.updateProgress(server, new UpdateProgressRequest(
                    "failed",
                    "Migration file exceeds size limit",
                    0, 0, null
            ));

            return ResponseEntity.status(413).body(Map.of(
                    "error", "Migration file exceeds size limit",
                    "message", String.format("File size (%.2fMB) exceeds the limit of %.2fMB.", fileSizeMB, limitMB),
                    "fileSize", file.getSize(),
                    "limit", fileSizeLimit
            ));
        }

        try {
            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);

            String uniqueFilename = "migration-" + UUID.randomUUID() + ".json";
            Path filePath = uploadPath.resolve(uniqueFilename);

            file.transferTo(filePath.toFile());

            migrationService.updateProgress(server, new UpdateProgressRequest(
                    "uploading_json",
                    "Migration file uploaded successfully. Starting data processing...",
                    0, 0, null
            ));

            migrationProcessor.processFileAsync(server, filePath);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Migration file uploaded successfully. Processing started.",
                    "fileSize", file.getSize()
            ));

        } catch (IOException e) {
            log.error("Failed to save migration file", e);

            migrationService.updateProgress(server, new UpdateProgressRequest(
                    "failed",
                    "Failed to save migration file: " + e.getMessage(),
                    0, 0, null
            ));

            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to save migration file",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/progress")
    public ResponseEntity<?> updateProgress(
            @RequestBody @Valid UpdateProgressRequest progressRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Map<String, Object> result = migrationService.updateProgress(server, progressRequest);

        if (Boolean.FALSE.equals(result.get("success"))) {
            return ResponseEntity.badRequest().body(Map.of("error", result.get("error")));
        }

        return ResponseEntity.ok(Map.of("success", true));
    }
}
