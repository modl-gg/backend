package gg.modl.backend.admin.dto.request;

import org.springframework.lang.Nullable;

public record UpdateRateLimitsRequest(
    @Nullable Integer rateLimitRequests,
    @Nullable Integer rateLimitWindow
) {}
