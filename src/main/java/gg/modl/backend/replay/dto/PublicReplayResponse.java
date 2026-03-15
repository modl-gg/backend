package gg.modl.backend.replay.dto;

public record PublicReplayResponse(
    String replayId,
    String mcVersion,
    long fileSize,
    long timestamp,
    String replayUrl,
    String status
) {}
