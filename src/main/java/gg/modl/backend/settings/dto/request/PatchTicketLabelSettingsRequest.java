package gg.modl.backend.settings.dto.request;

import gg.modl.backend.settings.data.Label;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PatchTicketLabelSettingsRequest(
    @NotNull @Min(0) Long expectedVersion,
    List<Label> labels
) {
}
