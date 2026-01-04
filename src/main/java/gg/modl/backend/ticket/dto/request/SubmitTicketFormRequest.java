package gg.modl.backend.ticket.dto.request;

import java.util.Map;

public record SubmitTicketFormRequest(
        String subject,
        Map<String, Object> formData,
        String creatorIdentifier
) {
}
