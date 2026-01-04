package gg.modl.backend.storage.dto.response;

import java.time.Instant;
import java.util.Map;

public record PresignUploadResponse(
        String presignedUrl,
        String key,
        Instant expiresAt,
        String method,
        Map<String, String> requiredHeaders
) {}
