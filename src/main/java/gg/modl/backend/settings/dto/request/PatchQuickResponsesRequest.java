package gg.modl.backend.settings.dto.request;

import gg.modl.backend.settings.data.QuickResponseSettings;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PatchQuickResponsesRequest(
    @NotNull @Min(0) Long expectedVersion,
    List<QuickResponseSettings.Category> categories
) {
}
