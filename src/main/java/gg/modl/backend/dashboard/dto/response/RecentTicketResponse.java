package gg.modl.backend.dashboard.dto.response;

import java.util.Date;

public record RecentTicketResponse(
        String id,
        String title,
        String initialMessage,
        String status,
        String priority,
        Date createdAt,
        String playerName,
        String type
) {
}
