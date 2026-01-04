package gg.modl.backend.storage.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.request.PresignUploadRequest;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.service.MediaValidationService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_MEDIA)
@RequiredArgsConstructor
public class PublicMediaController {
    private final S3StorageService s3StorageService;
    private final MediaValidationService validationService;
    private final StorageQuotaService quotaService;

    private static final Set<String> PUBLIC_ALLOWED_UPLOAD_TYPES = Set.of("ticket", "appeal");

    private static final long EVIDENCE_SIZE_LIMIT = 100L * 1024 * 1024; // 100MB (authenticated users only)
    private static final long TICKETS_SIZE_LIMIT = 10L * 1024 * 1024;   // 10MB for public ticket uploads
    private static final long APPEALS_SIZE_LIMIT = 10L * 1024 * 1024;   // 10MB
    private static final long ARTICLES_SIZE_LIMIT = 50L * 1024 * 1024;  // 50MB (authenticated users only)
    private static final long SERVER_ICONS_SIZE_LIMIT = 5L * 1024 * 1024; // 5MB (authenticated users only)

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

    @PostMapping("/presign")
    public ResponseEntity<?> getPresignedUploadUrl(
            @RequestBody @Valid PresignUploadRequest presignRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (!PUBLIC_ALLOWED_UPLOAD_TYPES.contains(presignRequest.uploadType())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Upload type not allowed for public uploads. Allowed: " + PUBLIC_ALLOWED_UPLOAD_TYPES
            ));
        }

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
}
