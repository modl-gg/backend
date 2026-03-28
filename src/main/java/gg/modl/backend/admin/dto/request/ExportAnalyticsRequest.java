package gg.modl.backend.admin.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record ExportAnalyticsRequest(
    @Nullable @Size(max = RequestValidationLimits.EXPORT_TYPE_MAX_LENGTH) String type,
    @Nullable @Size(max = RequestValidationLimits.EXPORT_RANGE_MAX_LENGTH) String range
) {}
