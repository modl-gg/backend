package gg.modl.backend.storage.controller;

import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import gg.modl.backend.storage.service.MediaAccessService;
import gg.modl.backend.storage.service.MediaValidationService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import gg.modl.backend.storage.service.StorageQuotaService;
import gg.modl.proto.modl.v1.ConfirmUploadRequest;
import gg.modl.proto.modl.v1.MediaConfigResponse;
import gg.modl.proto.modl.v1.PresignUploadRequest;
import jakarta.servlet.http.HttpServletRequest;
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
    private final StorageMetadataService storageMetadataService;

    private static final Set<String> PUBLIC_ALLOWED_UPLOAD_TYPES = Set.of("ticket", "tickets", "appeal");

    @GetMapping("/config")
    public ResponseEntity<MediaConfigResponse> getMediaConfig(HttpServletRequest request) {
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

        return ResponseEntity.ok(StorageProtoMapper.toMediaConfigResponse(isConfigured, supportedTypes, fileSizeLimits, cdnDomain));
    }

    @PostMapping("/presign")
    public ResponseEntity<?> getPresignedUploadUrl(
        @RequestBody PresignUploadRequest presignRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean isPremium = server.getPlan() == ServerPlan.PREMIUM;
        String uploadType = presignRequest.getUploadType();
        String entityId = presignRequest.hasEntityId() ? presignRequest.getEntityId() : null;
        String normalizedEntityId = entityId != null ? entityId.trim() : null;
        String accessToken = presignRequest.hasAccessToken() ? presignRequest.getAccessToken() : null;

        if (!PUBLIC_ALLOWED_UPLOAD_TYPES.contains(uploadType)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Upload type not allowed for public uploads. Allowed: " + PUBLIC_ALLOWED_UPLOAD_TYPES
            ));
        }

        MediaAccessService.AccessResult accessResult = mediaAccessService.validatePublicUploadAccess(
            server,
            uploadType,
            normalizedEntityId,
            accessToken
        );
        if (!accessResult.isAllowed()) {
            return toResponse(accessResult);
        }

        MediaValidationService.ValidationResult validation = validationService.validateMetadata(
            presignRequest.getFileName(),
            presignRequest.getContentType(),
            presignRequest.getFileSize(),
            normalizeUploadType(uploadType),
            isPremium
        );

        if (!validation.valid()) {
            return ResponseEntity.badRequest().body(Map.of("error", validation.error()));
        }

        if (!quotaService.canUpload(server, presignRequest.getFileSize())) {
            throw new ValidationException("Storage quota exceeded");
        }

        PresignUploadResponse response = s3StorageService.createPresignedUploadUrl(
            server,
            normalizeUploadType(uploadType),
            presignRequest.getFileName(),
            presignRequest.getContentType(),
            presignRequest.getFileSize(),
            normalizedEntityId
        );
        return ResponseEntity.ok(StorageProtoMapper.toPresignUploadResponse(response));
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
        @RequestBody ConfirmUploadRequest confirmRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String key = confirmRequest.getKey();

        if (!validationService.isKeyOwnedByServer(key, server.getDatabaseName())) {
            throw new ForbiddenException("Access denied");
        }

        String uploadType = validationService.extractUploadType(key);
        String entityId = validationService.extractEntityId(key);
        if (!PUBLIC_ALLOWED_UPLOAD_TYPES.contains(uploadType)) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Upload type not allowed for public confirmation"
            ));
        }
        if (entityId == null || entityId.isBlank()) {
            throw new ValidationException("Invalid upload key");
        }

        MediaAccessService.AccessResult accessResult = mediaAccessService.validatePublicUploadAccess(
            server,
            uploadType,
            entityId,
            confirmRequest.hasAccessToken() ? confirmRequest.getAccessToken() : null
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
        StorageQuotaService.ConfirmResult confirmResult =
            quotaService.confirmAndRecordFile(server, key, uploadDetails.size(), uploadDetails.contentType());
        switch (confirmResult) {
            case QUOTA_EXCEEDED -> {
                storageMetadataService.cleanupOrphanedUpload(server, key, uploadDetails.size(), uploadDetails.contentType());
                throw new ValidationException("Storage quota exceeded");
            }
            case RECORD_FAILED -> {
                storageMetadataService.cleanupOrphanedUpload(server, key, uploadDetails.size(), uploadDetails.contentType());
                return ResponseEntity.status(500).body(Map.of("error", "Failed to record upload"));
            }
            default -> {
                // SUCCESS
            }
        }

        return ResponseEntity.ok(StorageProtoMapper.toUploadResponse(uploadDetails));
    }
}
