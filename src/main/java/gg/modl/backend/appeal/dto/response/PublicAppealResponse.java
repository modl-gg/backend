package gg.modl.backend.appeal.dto.response;
import gg.modl.backend.ticket.data.TicketNote;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public record PublicAppealResponse(
        String id,
        String type,
        String subject,
        String status,
        String creator,
        String creatorUuid,
        Date created,
        Date date,
        boolean locked,
        List<TicketReply> replies,
        List<TicketReply> messages,
        List<TicketNote> notes,
        List<String> tags,
        Map<String, Object> data
) {
    public static PublicAppealResponse fromTicketResponse(TicketResponse appeal) {
        String creator = appeal.creator() != null ? appeal.creator() : "";
        String creatorUuid = appeal.creatorUuid() != null ? appeal.creatorUuid() : "";
        List<TicketReply> messages = appeal.messages() != null ? appeal.messages() : Collections.emptyList();
        List<TicketNote> notes = appeal.notes() != null ? appeal.notes() : Collections.emptyList();
        List<String> tags = appeal.tags() != null ? appeal.tags() : Collections.emptyList();
        Map<String, Object> data = appeal.data() != null ? appeal.data() : Map.of();

        return new PublicAppealResponse(
                appeal.id(),
                appeal.type(),
                appeal.subject(),
                appeal.status(),
                creator,
                creatorUuid,
                appeal.date(),
                appeal.date(),
                appeal.locked(),
                messages,
                messages,
                notes,
                tags,
                data
        );
    }
}
