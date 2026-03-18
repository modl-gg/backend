package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record AddTagRequest(
    @NotBlank @Size(max = RequestValidationLimits.TICKET_TAG_MAX_LENGTH) String tag,
    @Nullable @Size(max = RequestValidationLimits.STAFF_USERNAME_MAX_LENGTH) String staffName
) {
}
