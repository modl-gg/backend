package gg.modl.backend.replaylite.dto;

public record ReplayLitePublicResponse(
    String replayId,
    String mcVersion,
    long fileSize,
    long timestamp,
    String replayUrl,
    String status,
    boolean labeled
) {}
