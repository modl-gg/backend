package gg.modl.backend.admin.dto.request;

import org.springframework.lang.Nullable;

public record ExportAnalyticsRequest(
    @Nullable String type,
    @Nullable String range
) {}
