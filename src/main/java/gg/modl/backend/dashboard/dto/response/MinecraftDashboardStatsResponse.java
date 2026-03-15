package gg.modl.backend.dashboard.dto.response;

public record MinecraftDashboardStatsResponse(
    long unresolvedReports,
    long unresolvedTickets,
    long onlineStaff,
    long onlinePlayers,
    long activeBans,
    long activeMutes,
    long totalActivePunishments,
    long totalPlayers
) {
}
