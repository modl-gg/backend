package gg.modl.backend.analytics.dto.response;

import java.util.List;

public record AuditLogsAnalyticsResponse(
        List<LevelCount> byLevel,
        List<HourlyCount> hourlyTrend
) {
    public record LevelCount(String level, int count) {}
    public record HourlyCount(String hour, int count) {}
}
