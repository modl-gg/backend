package gg.modl.backend.migration.controller;

import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.migration.dto.UpdateProgressRequest;
import gg.modl.backend.migration.service.MigrationProcessor;
import gg.modl.backend.migration.service.MigrationService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
    private final MigrationProcessor migrationProcessor;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMigrationFile(
        @RequestParam("migrationFile") MultipartFile file,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (file.isEmpty()) {
            throw new ValidationException("No file uploaded");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".json")) {
            throw new ValidationException("Only JSON files are allowed");
        }

        Map<String, Object> sizeError = migrationService.validateFileSize(server, file);
        if (sizeError != null) {
            return ResponseEntity.status(413).body(sizeError);
        }

        Path filePath = migrationService.saveUploadedFile(file);

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
