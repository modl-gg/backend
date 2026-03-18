package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

public record UpdateTicketRequest(
    @Nullable @Size(max = RequestValidationLimits.TICKET_STATUS_MAX_LENGTH) String status,
    @Nullable Boolean locked,
    @Nullable @Valid AddReplyRequest newReply,
    @Nullable @Valid AddNoteRequest newNote,
    @Nullable @Size(max = RequestValidationLimits.TICKET_TAGS_MAX_ENTRIES) List<@Size(max = RequestValidationLimits.TICKET_TAG_MAX_LENGTH) String> tags,
    @Nullable @Size(max = RequestValidationLimits.TICKET_DATA_MAX_ENTRIES) Map<String, Object> data,
    @Nullable Boolean hidden
) {
}
