package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record SubmitTicketFormRequest(
        @NotBlank String subject,
        Map<String, Object> formData,
        List<Object> attachments,
        String creatorIdentifier
) {
}
