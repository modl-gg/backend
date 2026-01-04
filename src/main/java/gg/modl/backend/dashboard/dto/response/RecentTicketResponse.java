package gg.modl.backend.dashboard.dto.response;

import java.util.Date;

public record RecentTicketResponse(
        String id,
        String subject,
        String type,
        String status,
        String creator,
        Date created,
        boolean locked
) {
}
