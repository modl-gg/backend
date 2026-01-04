package gg.modl.backend.ticket.dto.request;

import java.util.List;
import java.util.Map;

public record UpdateTicketRequest(
        String status,
        Boolean locked,
        AddReplyRequest newReply,
        AddNoteRequest newNote,
        List<String> tags,
        Map<String, Object> data
) {
}
