package gg.modl.backend.audit.dto.response;

import java.util.Date;
import java.util.List;

public record StaffDetailsResponse(
        String username,
        String period,
        List<PunishmentDetail> punishments,
        List<TicketDetail> tickets,
        List<DailyActivity> dailyActivity,
        List<PunishmentTypeBreakdown> punishmentTypeBreakdown,
        int evidenceUploads,
        Summary summary
) {
    public record PunishmentDetail(
            String id,
            String playerId,
            String playerName,
            String type,
            String reason,
            String duration,
            Date issued,
            boolean active,
            boolean rolledBack
    ) {}

    public record TicketDetail(
            String id,
            String subject,
            String category,
            String status,
            Date lastActivity,
            int responseTime
    ) {}

    public record DailyActivity(
            String date,
            int punishments,
            int tickets,
            int evidence
    ) {}

    public record PunishmentTypeBreakdown(
            String type,
            int count
    ) {}

    public record Summary(
            int totalPunishments,
            int totalTickets,
            int avgResponseTime,
            int evidenceUploads
    ) {}
}
