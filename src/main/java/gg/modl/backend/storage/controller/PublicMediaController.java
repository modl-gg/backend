package gg.modl.backend.storage.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.dto.request.ConfirmUploadRequest;
import gg.modl.backend.storage.dto.request.PresignUploadRequest;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import gg.modl.backend.storage.service.MediaAccessService;
import gg.modl.backend.storage.service.MediaValidationService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_MEDIA)
@RequiredArgsConstructor
public class PublicMediaController {
    private final S3StorageService s3StorageService;
    private final MediaValidationService validationService;
    private final StorageQuotaService quotaService;
    private final MediaAccessService mediaAccessService;

    private static final Set<String> PUBLIC_ALLOWED_UPLOAD_TYPES = Set.of("ticket", "tickets", "appeal");

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getMediaConfig(HttpServletRequest request) {
        boolean isConfigured = s3StorageService.isConfigured();
        String cdnDomain = s3StorageService.getCdnDomain();
        Server server = RequestUtil.getRequestServer(request);
        boolean isPremium = server.getPlan() == ServerPlan.PREMIUM;

        Map<String, Object> supportedTypes = isConfigured
                                             ? validationService.getAllSupportedTypes()
                                             : Map.of("evidence", List.of(), "tickets", List.of(), "appeals", List.of(), "articles", List.of(), "server-icons",
                                                 List.of());

        Map<String, Object> fileSizeLimits = isConfigured
                                             ? validationService.getAllSizeLimits(isPremium)
                                             : Map.of("evidence", 0L, "tickets", 0L, "appeals", 0L, "articles", 0L, "server-icons", 0L);

        Map<String, Object> response = new HashMap<>();
        response.put("backblazeConfigured", isConfigured);
        response.put("supportedTypes", supportedTypes);
        response.put("fileSizeLimits", fileSizeLimits);
        response.put("cdnDomain", cdnDomain != null && !cdnDomain.isBlank() ? cdnDomain : null);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/presign")
    public ResponseEntity<?> getPresignedUploadUrl(
        @RequestBody @Valid PresignUploadRequest presignRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean isPremium = server.getPlan() == ServerPlan.PREMIUM;
        String normalizedEntityId = presignRequest.entityId() != null ? presignRequest.entityId().trim() : null;

        if (!PUBLIC_ALLOWED_UPLOAD_TYPES.contains(presignRequest.uploadType())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Upload type not allowed for public uploads. Allowed: " + PUBLIC_ALLOWED_UPLOAD_TYPES
            ));
        }

        MediaAccessService.AccessResult accessResult = mediaAccessService.validatePublicUploadAccess(
            server,
            presignRequest.uploadType(),
            normalizedEntityId,
            presignRequest.accessToken()
        );
        if (!accessResult.isAllowed()) {
            return toResponse(accessResult);
        }

        MediaValidationService.ValidationResult validation = validationService.validateMetadata(
            presignRequest.fileName(),
            presignRequest.contentType(),
            presignRequest.fileSize(),
            normalizeUploadType(presignRequest.uploadType()),
            isPremium
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
                normalizeUploadType(presignRequest.uploadType()),
                presignRequest.fileName(),
                presignRequest.contentType(),
                presignRequest.fileSize(),
                normalizedEntityId
            );
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String normalizeUploadType(String uploadType) {
        return "tickets".equals(uploadType) ? "ticket" : uploadType;
    }

    private ResponseEntity<?> toResponse(MediaAccessService.AccessResult result) {
        return switch (result.status()) {
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case DENIED -> ResponseEntity.status(403).body(Map.of("error", result.error()));
            case ALLOWED -> throw new IllegalStateException("Should not convert an allowed result to a response");
        };
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmUpload(
        @RequestBody @Valid ConfirmUploadRequest confirmRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String key = confirmRequest.key();

        if (!validationService.isKeyOwnedByServer(key, server.getDatabaseName())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        String uploadType = extractUploadType(key);
        String entityId = extractEntityId(key);
        if (!PUBLIC_ALLOWED_UPLOAD_TYPES.contains(uploadType)) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Upload type not allowed for public confirmation"
            ));
        }
        if (entityId == null || entityId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid upload key"));
        }

        MediaAccessService.AccessResult accessResult = mediaAccessService.validatePublicUploadAccess(
            server,
            uploadType,
            entityId,
            confirmRequest.accessToken()
        );
        if (!accessResult.isAllowed()) {
            return toResponse(accessResult);
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

    private String extractUploadType(String key) {
        String[] parts = key.split("/");
        return parts.length >= 2 ? parts[1] : "";
    }

    private String extractEntityId(String key) {
        String[] parts = key.split("/");
        return parts.length >= 4 ? parts[2] : null;
    }
}
