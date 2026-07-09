package gg.modl.backend.settings.dto.request;

import gg.modl.backend.settings.data.QuickResponseSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateQuickResponsesRequest(
    @NotNull
    @Size(max = QuickResponseSettings.MAX_CATEGORIES)
    @Valid
    List<QuickResponseSettings.Category> categories
) {}
