package gg.modl.backend.storage.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.storage.service.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_MEDIA)
@RequiredArgsConstructor
public class PublicMediaController {
    private final S3StorageService s3StorageService;

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
}
