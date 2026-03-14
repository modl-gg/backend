package gg.modl.backend.replay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record InitReplayUploadRequest(
    @NotBlank String mcVersion,
    @Positive long fileSize
) {}
