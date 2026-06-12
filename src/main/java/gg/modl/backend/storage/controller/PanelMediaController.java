package gg.modl.backend.storage.controller;

import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import gg.modl.backend.storage.service.MediaValidationService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import gg.modl.backend.storage.service.StorageQuotaService;
import gg.modl.proto.modl.v1.ConfirmUploadRequest;
import gg.modl.proto.modl.v1.MediaConfigResponse;
import gg.modl.proto.modl.v1.PresignUploadRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_MEDIA)
@RequiredArgsConstructor
public class PanelMediaController {
    private final S3StorageService s3StorageService;
    private final StorageQuotaService quotaService;
    private final MediaValidationService validationService;
    private final StorageMetadataService storageMetadataService;

    @GetMapping("/config")
    public ResponseEntity<MediaConfigResponse> getMediaConfig(HttpServletRequest request) {
        boolean isConfigured = s3StorageService.isConfigured();
        String cdnDomain = s3StorageService.getCdnDomain();
        Server server = RequestUtil.getRequestServer(request);
        boolean isPremium = server.getPlan() == ServerPlan.PREMIUM;

        return ResponseEntity.ok(StorageProtoMapper.toMediaConfigResponse(
            isConfigured,
            validationService.getAllSupportedTypes(),
            validationService.getAllSizeLimits(isPremium),
            cdnDomain
        ));
    }

    @DeleteMapping("/{*key}")
    public ResponseEntity<?> deleteFile(
        @PathVariable String key,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;

        if (!validationService.isKeyOwnedByServer(normalizedKey, server.getDatabaseName())) {
            throw new ForbiddenException("Access denied");
        }

        boolean deleted = s3StorageService.deleteFile(normalizedKey);
        if (deleted) {
            storageMetadataService.removeFile(server, normalizedKey);
            return ResponseEntity.ok(Map.of("message", "File deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/presign")
    public ResponseEntity<?> getPresignedUploadUrl(
        @RequestBody PresignUploadRequest presignRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean isPremium = server.getPlan() == ServerPlan.PREMIUM;

        MediaValidationService.ValidationResult validation = validationService.validateMetadata(
            presignRequest.getFileName(),
            presignRequest.getContentType(),
            presignRequest.getFileSize(),
            presignRequest.getUploadType(),
            isPremium
        );

        if (!validation.valid()) {
            return ResponseEntity.badRequest().body(Map.of("error", validation.error()));
        }

        if (!quotaService.canUpload(server, presignRequest.getFileSize())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Storage quota exceeded"));
        }

        PresignUploadResponse response = s3StorageService.createPresignedUploadUrl(
            server,
            presignRequest.getUploadType(),
            presignRequest.getFileName(),
            presignRequest.getContentType(),
            presignRequest.getFileSize(),
            presignRequest.hasEntityId() ? presignRequest.getEntityId() : null
        );
        return ResponseEntity.ok(StorageProtoMapper.toPresignUploadResponse(response));
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

        UploadResponse uploadDetails = s3StorageService.getUploadDetails(key);
        if (uploadDetails == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Upload not found",
                "message", "The file was not uploaded or the presigned URL expired"
            ));
        }
        if (!quotaService.confirmAndRecordFile(server, key, uploadDetails.size(), uploadDetails.contentType())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Storage quota exceeded"));
        }

        return ResponseEntity.ok(StorageProtoMapper.toUploadResponse(uploadDetails));
    }
}
