package gg.modl.backend.ticket.dto.response;

import gg.modl.backend.ticket.data.TicketNote;
import gg.modl.backend.ticket.data.TicketReply;

import java.util.Date;
import java.util.List;
import java.util.Map;

public record TicketResponse(
        String id,
        String type,
        String category,
        String subject,
        String status,
        String creator,
        String creatorUuid,
        String reportedBy,
        String reportedPlayer,
        String reportedPlayerUuid,
        Date date,
        boolean locked,
        List<TicketReply> messages,
        List<TicketNote> notes,
        List<String> tags,
        Map<String, Object> formData,
        Map<String, Object> data,
        List<Map<String, Object>> chatMessages
) {
}
