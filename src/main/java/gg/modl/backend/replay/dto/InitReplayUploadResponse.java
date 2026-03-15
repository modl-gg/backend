package gg.modl.backend.replay.dto;

import java.util.Map;

public record InitReplayUploadResponse(
    String replayId,
    String uploadUrl,
    String method,
    Map<String, String> requiredHeaders
) {}
