package gg.modl.backend.settings.dto.request;

import gg.modl.backend.settings.data.OffenderThresholdSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PatchStatusThresholdSettingsRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotNull @Valid OffenderThresholdSettings settings
) {
}
