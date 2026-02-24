package gg.modl.backend.settings.dto.request;

import gg.modl.backend.settings.data.QuickResponseSettings;

import java.util.List;

public record UpdateQuickResponsesRequest(
        List<QuickResponseSettings.Category> categories
) {}
