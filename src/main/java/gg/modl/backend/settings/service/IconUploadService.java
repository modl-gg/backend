package gg.modl.backend.settings.service;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import gg.modl.backend.storage.service.StorageQuotaService;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class IconUploadService {
    private final S3StorageService s3StorageService;
    private final StorageQuotaService storageQuotaService;
    private final StorageMetadataService storageMetadataService;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "image/svg+xml"
    );
    private static final long MAX_ICON_SIZE = 2 * 1024 * 1024;

    public ResponseEntity<?> uploadIcon(Server server, MultipartFile file, String iconType) {
        ResponseEntity<?> validationError = validateUpload(server, file, iconType);
        if (validationError != null) {
            return validationError;
        }

        try {
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "icon";
            S3StorageService.UploadFileResult result = s3StorageService.uploadFile(
                server, "icons/" + iconType, fileName, file.getContentType(), file.getBytes()
            );
            storageMetadataService.recordFile(server, result.key(), file.getSize(), file.getContentType());
            return ResponseEntity.ok(Map.of("url", result.cdnUrl()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to read file"));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload file"));
        }
    }

    private ResponseEntity<?> validateUpload(Server server, MultipartFile file, String iconType) {
        if (!iconType.equals("homepage") && !iconType.equals("panel")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid icon type. Must be 'homepage' or 'panel'."));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file type. Allowed: PNG, JPEG, GIF, WebP, SVG"));
        }
        if (file.getSize() > MAX_ICON_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large. Maximum size is 2MB."));
        }
        if (!s3StorageService.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of("error", "File storage is not configured"));
        }
        if (!storageQuotaService.canUpload(server, file.getSize())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Storage quota exceeded"));
        }
        return null;
    }
}
