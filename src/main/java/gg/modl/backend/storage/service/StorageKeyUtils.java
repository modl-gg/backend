package gg.modl.backend.storage.service;

import gg.modl.backend.server.data.Server;
import java.util.UUID;

public final class StorageKeyUtils {
    private StorageKeyUtils() {
    }

    public static String buildKey(Server server, String uploadType, String fileName, String entityId) {
        String safeUploadType = sanitizeSegment(uploadType, "other");
        String safeFileName = sanitizeFileName(fileName);

        if (entityId != null && !entityId.isBlank()) {
            String safeEntityId = sanitizeSegment(entityId, UUID.randomUUID().toString());
            String folder = "ticket".equals(safeUploadType) ? "tickets" : safeUploadType;
            String uniqueName = UUID.randomUUID() + "-" + safeFileName;
            return String.format("%s/%s/%s/%s", server.getDatabaseName(), folder, safeEntityId, uniqueName);
        }

        String uuid = UUID.randomUUID().toString();
        int dotIndex = safeFileName.lastIndexOf('.');
        String extension = dotIndex >= 0 ? safeFileName.substring(dotIndex) : "";
        return String.format("%s/%s/%s%s", server.getDatabaseName(), safeUploadType, uuid, extension);
    }

    public static String extractFileName(String key) {
        return key.substring(key.lastIndexOf("/") + 1);
    }

    public static String categorizeFile(String key) {
        if (key.contains("/evidence/")) {
            return "evidence";
        }
        if (key.contains("/tickets/") || key.contains("/ticket/")) {
            return "ticket";
        }
        if (key.contains("/logs/")) {
            return "logs";
        }
        if (key.contains("/backup/")) {
            return "backup";
        }
        if (key.contains("/replays/")) {
            return "replay";
        }
        return "other";
    }

    public static String normalizeUploadType(String uploadType) {
        return "tickets".equals(uploadType) ? "ticket" : uploadType;
    }

    public static String stripLeadingSlash(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "upload.bin";
        }

        String basename = fileName.replace('\\', '/');
        int slashIndex = basename.lastIndexOf('/');
        if (slashIndex >= 0) {
            basename = basename.substring(slashIndex + 1);
        }

        return sanitizeAllowed(basename, "upload.bin");
    }

    private static String sanitizeSegment(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return sanitizeAllowed(value, fallback);
    }

    private static String sanitizeAllowed(String value, String fallback) {
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isBlank()) {
            return fallback;
        }
        if (sanitized.length() > 128) {
            return sanitized.substring(0, 128);
        }
        return sanitized;
    }
}
