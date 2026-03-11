package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record SubmitTicketFormRequest(
    @NotBlank String subject,
    @Email @Size(min = 3, max = 254) String creatorEmail,
    Map<String, Object> formData,
    List<Object> attachments,
    String creatorIdentifier
) {
}
