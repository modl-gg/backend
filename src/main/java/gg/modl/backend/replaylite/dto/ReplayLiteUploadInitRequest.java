package gg.modl.backend.replaylite.dto;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReplayLiteUploadInitRequest(
    @NotNull UUID pluginServerUuid,
    @Positive
    @Max(RequestValidationLimits.REPLAY_LITE_MAX_REQUESTED_SIZE_BYTES)
    long requestedSize,
    @NotBlank
    @Size(max = RequestValidationLimits.REPLAY_LITE_MC_VERSION_MAX_LENGTH)
    @Pattern(regexp = "^[A-Za-z0-9_.+\\-]+$")
    String mcVersion
) {}
