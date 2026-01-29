package gg.modl.backend.storage.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MediaValidationService {
    private static final long DEFAULT_MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            ".exe", ".bat", ".cmd", ".com", ".msi", ".scr", ".pif",
            ".js", ".vbs", ".wsf", ".jar", ".sh", ".ps1", ".php",
            ".asp", ".aspx", ".jsp", ".cgi", ".pl", ".py", ".rb"
    );

    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
            "evidence", Set.of("image/png", "image/jpeg", "image/gif", "image/apng", "image/webp", "video/mp4", "video/webm", "video/quicktime",
            "video/x-matroska", "application/pdf", "text/plain", "text/markdown"),
            "ticket", Set.of("image/png", "image/jpeg", "image/gif", "image/apng", "image/webp", "video/mp4", "video/webm", "video/quicktime",
            "video/x-matroska", "application/pdf", "text/plain", "text/markdown"),
            "appeal", Set.of("image/png", "image/jpeg", "image/gif", "image/apng", "image/webp", "video/mp4", "video/webm", "video/quicktime",
            "video/x-matroska", "application/pdf", "text/plain", "text/markdown"),
            "article", Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "image/apng", "text/plain", "text/markdown"),
            "server-icon", Set.of("image/png", "image/jpeg", "image/webp", "image/gif", "image/apng")
    );

    private static final Map<String, Long> MAX_SIZES = Map.of(
            "evidence", 100L * 1024 * 1024, // 100 mb
            "ticket", 100L * 1024 * 1024, // 100 mb
            "appeal", 50L * 1024 * 1024, // 50 mb
            "article", 50L * 1024 * 1024, // 50 mb
            "server-icon", 10L * 1024 * 1024 //  10 mb
    );

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

        Set<String> allowedTypes = getAllowedTypes(uploadType);
        if (allowedTypes.isEmpty()) {
            return new ValidationResult(false, "Invalid upload type");
        }

        if (!allowedTypes.contains(contentType)) {
            return new ValidationResult(false, "File type not allowed for " + uploadType + ". Allowed: " + allowedTypes);
        }

        long maxSize = getMaxSize(uploadType);
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
        return MAX_SIZES.getOrDefault(uploadType, DEFAULT_MAX_FILE_SIZE);
    }

    public Map<String, Object> getAllSupportedTypes() {
        return Map.of(
                "evidence", List.copyOf(ALLOWED_TYPES.get("evidence")),
                "tickets", List.copyOf(ALLOWED_TYPES.get("ticket")),
                "appeals", List.copyOf(ALLOWED_TYPES.get("appeal")),
                "articles", List.copyOf(ALLOWED_TYPES.get("article")),
                "server-icons", List.copyOf(ALLOWED_TYPES.get("server-icon"))
        );
    }

    public Map<String, Object> getAllSizeLimits() {
        return Map.of(
                "evidence", MAX_SIZES.get("evidence"),
                "tickets", MAX_SIZES.get("ticket"),
                "appeals", MAX_SIZES.get("appeal"),
                "articles", MAX_SIZES.get("article"),
                "server-icons", MAX_SIZES.get("server-icon")
        );
    }

    public record ValidationResult(boolean valid, String error) {}
}
