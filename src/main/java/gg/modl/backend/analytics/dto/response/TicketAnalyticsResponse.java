package gg.modl.backend.analytics.dto.response;

import java.util.List;

public record TicketAnalyticsResponse(
        List<StatusCount> byStatus,
        List<CategoryCount> byCategory,
        List<CategoryResolutionTime> avgResolutionByCategory,
        List<DailyTicket> dailyTickets
) {
    public record StatusCount(String status, int count) {}
    public record CategoryCount(String category, int count) {}
    public record CategoryResolutionTime(String category, double avgHours) {}
    public record DailyTicket(String date, int count) {}
}
