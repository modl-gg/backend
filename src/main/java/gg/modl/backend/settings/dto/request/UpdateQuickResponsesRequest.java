package gg.modl.backend.settings.dto.request;

import gg.modl.backend.settings.data.QuickResponseSettings;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateQuickResponsesRequest(
    @NotNull List<QuickResponseSettings.Category> categories
) {}
