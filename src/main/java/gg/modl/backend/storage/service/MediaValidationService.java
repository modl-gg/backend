package gg.modl.backend.storage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class MediaValidationService {

    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            ".exe", ".bat", ".cmd", ".com", ".msi", ".scr", ".pif",
            ".js", ".vbs", ".wsf", ".jar", ".sh", ".ps1", ".php",
            ".asp", ".aspx", ".jsp", ".cgi", ".pl", ".py", ".rb"
    );

    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
            "evidence", Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "video/mp4", "video/webm"),
            "ticket", Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf"),
            "appeal", Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf"),
            "article", Set.of("image/png", "image/jpeg", "image/gif", "image/webp"),
            "server-icon", Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml")
    );

    private static final Map<String, Long> MAX_SIZES = Map.of(
            "evidence", 50L * 1024 * 1024,
            "ticket", 10L * 1024 * 1024,
            "appeal", 10L * 1024 * 1024,
            "article", 5L * 1024 * 1024,
            "server-icon", 2L * 1024 * 1024
    );

    public ValidationResult validate(MultipartFile file, String uploadType) {
        if (file == null || file.isEmpty()) {
            return new ValidationResult(false, "No file provided");
        }

        String filename = file.getOriginalFilename();
        if (filename != null) {
            String lowerFilename = filename.toLowerCase();
            for (String ext : DANGEROUS_EXTENSIONS) {
                if (lowerFilename.endsWith(ext)) {
                    log.warn("Blocked upload of potentially dangerous file: {}", filename);
                    return new ValidationResult(false, "File type not allowed");
                }
            }
        }

        Set<String> allowedTypes = ALLOWED_TYPES.getOrDefault(uploadType, Set.of());
        if (allowedTypes.isEmpty()) {
            return new ValidationResult(false, "Invalid upload type");
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            return new ValidationResult(false, "File type not allowed for " + uploadType + ". Allowed: " + allowedTypes);
        }

        long maxSize = MAX_SIZES.getOrDefault(uploadType, MAX_FILE_SIZE_BYTES);
        if (file.getSize() > maxSize) {
            return new ValidationResult(false, "File exceeds maximum size of " + formatBytes(maxSize));
        }

        return new ValidationResult(true, null);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format("%.0f KB", bytes / 1024.0);
        } else {
            return String.format("%.0f MB", bytes / (1024.0 * 1024));
        }
    }

    public ValidationResult validateMetadata(String fileName, String contentType, long fileSize, String uploadType) {
        if (fileName == null || fileName.isBlank()) {
            return new ValidationResult(false, "File name is required");
        }

        if (contentType == null || contentType.isBlank()) {
            return new ValidationResult(false, "Content type is required");
        }

        String lowerFilename = fileName.toLowerCase();
        for (String ext : DANGEROUS_EXTENSIONS) {
            if (lowerFilename.endsWith(ext)) {
                log.warn("Blocked presign request for potentially dangerous file: {}", fileName);
                return new ValidationResult(false, "File type not allowed");
            }
        }

        Set<String> allowedTypes = ALLOWED_TYPES.getOrDefault(uploadType, Set.of());
        if (allowedTypes.isEmpty()) {
            return new ValidationResult(false, "Invalid upload type");
        }

        if (!allowedTypes.contains(contentType)) {
            return new ValidationResult(false, "File type not allowed for " + uploadType + ". Allowed: " + allowedTypes);
        }

        long maxSize = MAX_SIZES.getOrDefault(uploadType, MAX_FILE_SIZE_BYTES);
        if (fileSize > maxSize) {
            return new ValidationResult(false, "File exceeds maximum size of " + formatBytes(maxSize));
        }

        if (fileSize <= 0) {
            return new ValidationResult(false, "Invalid file size");
        }

        return new ValidationResult(true, null);
    }

    public Set<String> getAllowedTypes(String uploadType) {
        return ALLOWED_TYPES.getOrDefault(uploadType, Set.of());
    }

    public long getMaxSize(String uploadType) {
        return MAX_SIZES.getOrDefault(uploadType, MAX_FILE_SIZE_BYTES);
    }

    public record ValidationResult(boolean valid, String error) {}
}
