package gg.modl.backend.storage.controller;

import gg.modl.backend.infrastructure.authorization.RequiresPanelPermission;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.replay.service.ReplayDeletionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.service.MediaValidationService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageKeyUtils;
import gg.modl.backend.storage.service.StorageMetadataService;
import gg.modl.backend.storage.service.UploadOrchestrationService;
import gg.modl.proto.modl.v1.ConfirmUploadRequest;
import gg.modl.proto.modl.v1.MediaConfigResponse;
import gg.modl.proto.modl.v1.PresignUploadRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
@RequiresPanelPermission(view = "admin.settings.view.content", modify = "admin.settings.modify.content")
@RequiredArgsConstructor
public class PanelMediaController {
    private final S3StorageService s3StorageService;
    private final MediaValidationService validationService;
    private final StorageMetadataService storageMetadataService;
    private final UploadOrchestrationService uploadOrchestrationService;
    private final ReplayDeletionService replayDeletionService;

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
        String normalizedKey = StorageKeyUtils.stripLeadingSlash(key);
        validationService.assertKeyOwnedByServer(server, normalizedKey);

        boolean deleted = s3StorageService.deleteFile(normalizedKey);
        if (deleted) {
            storageMetadataService.removeFile(server, normalizedKey);
            replayDeletionService.reconcileDeletedStorageKeys(server, List.of(normalizedKey));
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
        UploadOrchestrationService.PresignOutcome outcome = uploadOrchestrationService.presign(server,
            new UploadOrchestrationService.UploadPresignRequest(
                presignRequest.getUploadType(),
                presignRequest.getFileName(),
                presignRequest.getContentType(),
                presignRequest.getFileSize(),
                presignRequest.hasEntityId() ? presignRequest.getEntityId() : null,
                server.getPlan() == ServerPlan.PREMIUM,
                false
            ));

        return switch (outcome.status()) {
            case SUCCESS -> ResponseEntity.ok(StorageProtoMapper.toPresignUploadResponse(outcome.upload()));
            case VALIDATION_FAILED, QUOTA_EXCEEDED, TEMP_LIMIT_EXCEEDED ->
                ResponseEntity.badRequest().body(Map.of("error", outcome.message() != null ? outcome.message() : "Upload rejected"));
        };
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmUpload(
        @RequestBody ConfirmUploadRequest confirmRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        UploadOrchestrationService.ConfirmOutcome outcome =
            uploadOrchestrationService.confirm(server, confirmRequest.getKey(), false);

        return switch (outcome.status()) {
            case SUCCESS -> ResponseEntity.ok(StorageProtoMapper.toUploadResponse(outcome.upload()));
            case UPLOAD_NOT_FOUND -> ResponseEntity.badRequest().body(Map.of(
                "error", "Upload not found",
                "message", "The file was not uploaded or the presigned URL expired"
            ));
            case QUOTA_EXCEEDED, TEMP_LIMIT_EXCEEDED -> ResponseEntity.badRequest().body(Map.of("error", "Storage quota exceeded"));
            case RECORD_FAILED -> ResponseEntity.internalServerError().body(Map.of("error", "Failed to record upload"));
        };
    }
}
