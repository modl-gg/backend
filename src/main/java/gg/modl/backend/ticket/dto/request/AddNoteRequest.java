package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record AddNoteRequest(
    @NotBlank @Size(max = RequestValidationLimits.TICKET_NOTE_TEXT_MAX_LENGTH) String text,
    @NotBlank @Size(max = RequestValidationLimits.TICKET_REPLY_NAME_MAX_LENGTH) String issuerName,
    @Nullable @Size(max = RequestValidationLimits.TICKET_NOTE_AVATAR_MAX_LENGTH) String issuerAvatar
) {
}
