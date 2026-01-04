package gg.modl.backend.dashboard.dto.response;

public record DashboardMetricsResponse(
        long totalTickets,
        long openTickets,
        long totalPlayers,
        long totalPunishments,
        long activePunishments,
        long totalStaff,
        int ticketsTrend,
        int playersTrend
) {
}
