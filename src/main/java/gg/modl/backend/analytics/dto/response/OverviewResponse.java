package gg.modl.backend.analytics.dto.response;

public record OverviewResponse(
        long totalTickets,
        long totalPlayers,
        long totalStaff,
        long activeTickets,
        int ticketChange,
        int playerChange
) {
}
