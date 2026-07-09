package gg.modl.backend.settings.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PatchReplayRetentionSettingsRequest(
    @NotNull @Min(0) Long expectedVersion,
    Boolean enabled,
    @Min(1) @Max(365) Integer days
) {
}
