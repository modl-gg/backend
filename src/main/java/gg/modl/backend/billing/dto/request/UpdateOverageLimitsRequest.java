package gg.modl.backend.billing.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateOverageLimitsRequest(
    @NotNull(message = "maxStorageOverageGB is required")
    @Min(value = 0, message = "maxStorageOverageGB must be at least 0")
    @Max(value = 2000, message = "maxStorageOverageGB cannot exceed 2000")
    Integer maxStorageOverageGB,

    @NotNull(message = "maxAiOverageRequests is required")
    @Min(value = 0, message = "maxAiOverageRequests must be at least 0")
    @Max(value = 5000, message = "maxAiOverageRequests cannot exceed 5000")
    Integer maxAiOverageRequests
) {}
