package gg.modl.backend.replay.dto;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InitReplayUploadRequest(
    @NotBlank String mcVersion,
    @Positive long fileSize,
    @Size(max = RequestValidationLimits.ID_MAX_LENGTH)
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    String targetUuid,
    @Size(max = RequestValidationLimits.LOG_USERNAME_MAX_LENGTH)
    String targetName
) {}
