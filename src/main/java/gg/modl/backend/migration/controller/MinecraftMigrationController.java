package gg.modl.backend.migration.controller;

import gg.modl.backend.migration.dto.UpdateProgressRequest;
import gg.modl.backend.migration.service.MigrationService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_MIGRATION)
@RequiredArgsConstructor
public class MinecraftMigrationController {
    private final MigrationService migrationService;

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
            double fileSizeGB = file.getSize() / (1024.0 * 1024.0 * 1024.0);
            double limitGB = fileSizeLimit / (1024.0 * 1024.0 * 1024.0);

            migrationService.updateProgress(server, new UpdateProgressRequest(
                    "failed",
                    "Migration file exceeds size limit",
                    0, 0, null
            ));

            return ResponseEntity.status(413).body(Map.of(
                    "error", "Migration file exceeds size limit",
                    "message", String.format("File size (%.2fGB) exceeds the limit of %.2fGB.", fileSizeGB, limitGB),
                    "fileSize", file.getSize(),
                    "limit", fileSizeLimit
            ));
        }

        migrationService.updateProgress(server, new UpdateProgressRequest(
                "uploading_json",
                "Migration file uploaded successfully. Starting data processing...",
                0, 0, null
        ));

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

        Map<String, Object> result = migrationService.updateProgress(server, progressRequest);

        if (Boolean.FALSE.equals(result.get("success"))) {
            return ResponseEntity.badRequest().body(Map.of("error", result.get("error")));
        }

        return ResponseEntity.ok(Map.of("success", true));
    }
}
