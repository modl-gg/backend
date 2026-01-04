package gg.modl.backend.analytics.dto.response;

import java.util.List;

public record PunishmentAnalyticsResponse(
        List<TypeCount> byType,
        List<SeverityCount> bySeverity,
        List<DailyPunishment> dailyPunishments,
        List<StaffPunishment> byStaff
) {
    public record TypeCount(String type, int count) {}
    public record SeverityCount(String severity, int count) {}
    public record DailyPunishment(String date, int count) {}
    public record StaffPunishment(String username, int count) {}
}
