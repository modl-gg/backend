package gg.modl.backend.audit.dto.response;

import java.util.Date;

public record StaffPerformanceResponse(
        String id,
        String username,
        String role,
        int totalActions,
        int ticketResponses,
        int punishmentsIssued,
        int avgResponseTime,
        Date lastActive
) {
}
