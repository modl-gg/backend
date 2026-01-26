package gg.modl.backend.storage.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.request.ConfirmUploadRequest;
import gg.modl.backend.storage.dto.request.PresignUploadRequest;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import gg.modl.backend.storage.service.MediaValidationService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_MEDIA)
@RequiredArgsConstructor
public class PanelMediaController {
    private final S3StorageService s3StorageService;
    private final StorageQuotaService quotaService;
    private final MediaValidationService validationService;

    private static final long EVIDENCE_SIZE_LIMIT = 100L * 1024 * 1024;
    private static final long TICKETS_SIZE_LIMIT = 10L * 1024 * 1024;
    private static final long APPEALS_SIZE_LIMIT = 10L * 1024 * 1024;
    private static final long ARTICLES_SIZE_LIMIT = 50L * 1024 * 1024;
    private static final long SERVER_ICONS_SIZE_LIMIT = 5L * 1024 * 1024;

    private static final List<String> IMAGE_TYPES = List.of("image/png", "image/jpeg", "image/gif");
    private static final List<String> DOCUMENT_TYPES = List.of("image/png", "image/jpeg", "image/gif", "video/mp4", "application/pdf");
    private static final List<String> ICON_TYPES = List.of("image/png", "image/jpeg");

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getMediaConfig() {
        boolean isConfigured = s3StorageService.isConfigured();

        Map<String, Object> supportedTypes = Map.of(
                "evidence", isConfigured ? DOCUMENT_TYPES : List.of(),
                "tickets", isConfigured ? DOCUMENT_TYPES : List.of(),
                "appeals", isConfigured ? DOCUMENT_TYPES : List.of(),
                "articles", isConfigured ? IMAGE_TYPES : List.of(),
                "server-icons", isConfigured ? ICON_TYPES : List.of()
        );

        Map<String, Object> fileSizeLimits = Map.of(
                "evidence", isConfigured ? EVIDENCE_SIZE_LIMIT : 0L,
                "tickets", isConfigured ? TICKETS_SIZE_LIMIT : 0L,
                "appeals", isConfigured ? APPEALS_SIZE_LIMIT : 0L,
                "articles", isConfigured ? ARTICLES_SIZE_LIMIT : 0L,
                "server-icons", isConfigured ? SERVER_ICONS_SIZE_LIMIT : 0L
        );

        Map<String, Object> response = Map.of(
                "backblazeConfigured", isConfigured,
                "supportedTypes", supportedTypes,
                "fileSizeLimits", fileSizeLimits
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload/{type}")
    public ResponseEntity<?> uploadFile(
            @PathVariable String type,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        MediaValidationService.ValidationResult validation = validationService.validate(file, type);
        if (!validation.valid()) {
            return ResponseEntity.badRequest().body(Map.of("error", validation.error()));
        }

        if (!quotaService.canUpload(server, file.getSize())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Storage quota exceeded"));
        }

        try {
            UploadResponse response = s3StorageService.uploadFile(server, file, type);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload file"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{*key}")
    public ResponseEntity<?> deleteFile(
            @PathVariable String key,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (!key.startsWith(server.getDatabaseName() + "/")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        boolean deleted = s3StorageService.deleteFile(key);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "File deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/presign")
    public ResponseEntity<?> getPresignedUploadUrl(
            @RequestBody @Valid PresignUploadRequest presignRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        MediaValidationService.ValidationResult validation = validationService.validateMetadata(
                presignRequest.fileName(),
                presignRequest.contentType(),
                presignRequest.fileSize(),
                presignRequest.uploadType()
        );

        if (!validation.valid()) {
            return ResponseEntity.badRequest().body(Map.of("error", validation.error()));
        }

        if (!quotaService.canUpload(server, presignRequest.fileSize())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Storage quota exceeded"));
        }

        try {
            PresignUploadResponse response = s3StorageService.createPresignedUploadUrl(
                    server,
                    presignRequest.uploadType(),
                    presignRequest.fileName(),
                    presignRequest.contentType(),
                    presignRequest.fileSize()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmUpload(
            @RequestBody @Valid ConfirmUploadRequest confirmRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        String key = confirmRequest.key();

        if (!key.startsWith(server.getDatabaseName() + "/")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        UploadResponse uploadDetails = s3StorageService.getUploadDetails(key);
        if (uploadDetails == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Upload not found",
                    "message", "The file was not uploaded or the presigned URL expired"
            ));
        }

        return ResponseEntity.ok(uploadDetails);
    }
}
