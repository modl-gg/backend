package gg.modl.backend.admin.dto.response;

import gg.modl.backend.admin.data.SystemConfig;
import java.util.Date;

public record AdminRateLimitStatus(
    SystemConfig.PerformanceConfig current,
    boolean active,
    Date resetTime
) {
}
