package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

public record SubmitTicketFormRequest(
    @NotBlank @Size(max = RequestValidationLimits.TICKET_SUBJECT_MAX_LENGTH) String subject,
    @Nullable @Email @Size(min = 3, max = RequestValidationLimits.EMAIL_MAX_LENGTH) String creatorEmail,
    @Nullable @Size(max = RequestValidationLimits.TICKET_FORM_DATA_MAX_ENTRIES) Map<String, Object> formData,
    @Nullable @Size(max = RequestValidationLimits.TICKET_ATTACHMENTS_MAX_ENTRIES) List<Object> attachments,
    @Nullable @Size(max = RequestValidationLimits.TICKET_CREATOR_IDENTIFIER_MAX_LENGTH) String creatorIdentifier
) {
}
