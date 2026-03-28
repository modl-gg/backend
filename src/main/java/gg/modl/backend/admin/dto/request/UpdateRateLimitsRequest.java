package gg.modl.backend.admin.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.lang.Nullable;

public record UpdateRateLimitsRequest(
    @Nullable
    @Min(RequestValidationLimits.RATE_LIMIT_REQUESTS_MIN)
    @Max(RequestValidationLimits.RATE_LIMIT_REQUESTS_MAX)
    Integer rateLimitRequests,
    @Nullable
    @Min(RequestValidationLimits.RATE_LIMIT_WINDOW_SECONDS_MIN)
    @Max(RequestValidationLimits.RATE_LIMIT_WINDOW_SECONDS_MAX)
    Integer rateLimitWindow
) {}
