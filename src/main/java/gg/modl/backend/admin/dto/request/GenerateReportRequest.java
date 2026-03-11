package gg.modl.backend.admin.dto.request;

import org.springframework.lang.Nullable;

public record GenerateReportRequest(
    @Nullable String type,
    @Nullable String range,
    @Nullable String format
) {}
