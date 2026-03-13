package gg.modl.backend.billing.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateStorageLimitRequest(
    @NotNull(message = "maxStorageLimitBytes is required")
    @Positive(message = "maxStorageLimitBytes must be greater than 0")
    Long maxStorageLimitBytes
) {}
