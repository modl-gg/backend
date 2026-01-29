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
import java.util.HashMap;
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

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getMediaConfig() {
        boolean isConfigured = s3StorageService.isConfigured();
        String cdnDomain = s3StorageService.getCdnDomain();

        Map<String, Object> response = new HashMap<>();
        response.put("backblazeConfigured", isConfigured);
        response.put("supportedTypes", validationService.getAllSupportedTypes());
        response.put("fileSizeLimits", validationService.getAllSizeLimits());
        response.put("cdnDomain", cdnDomain != null && !cdnDomain.isBlank() ? cdnDomain : null);

        return ResponseEntity.ok(response);
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
