package gg.modl.backend.settings.dto.request;

import gg.modl.backend.settings.data.TicketFormSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PatchTicketFormSettingsRequest(
    @NotNull @Min(0) Long expectedVersion,
    @NotNull @Valid TicketFormSettings settings
) {
}
