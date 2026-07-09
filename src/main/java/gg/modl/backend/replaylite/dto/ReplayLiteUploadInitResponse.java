package gg.modl.backend.replaylite.dto;

import java.time.Instant;
import java.util.Map;

public record ReplayLiteUploadInitResponse(
    String replayId,
    String uploadUrl,
    String method,
    Map<String, String> requiredHeaders,
    Instant expiresAt
) {}
