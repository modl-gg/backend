package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.lang.Nullable;

public record AddReplyRequest(
    @NotBlank @Size(max = RequestValidationLimits.TICKET_REPLY_NAME_MAX_LENGTH) String name,
    @NotBlank @Size(max = RequestValidationLimits.TICKET_REPLY_CONTENT_MAX_LENGTH) String content,
    @Nullable @Size(max = RequestValidationLimits.TICKET_REPLY_TYPE_MAX_LENGTH) String type,
    boolean staff,
    @Nullable @Size(max = RequestValidationLimits.TICKET_REPLY_AVATAR_MAX_LENGTH) String avatar,
    @Nullable @Size(max = RequestValidationLimits.TICKET_REPLY_ATTACHMENTS_MAX_ENTRIES) List<Object> attachments,
    @Nullable @Size(max = RequestValidationLimits.TICKET_REPLY_ACTION_MAX_LENGTH) String action,
    @Nullable @Size(max = RequestValidationLimits.TICKET_CREATOR_IDENTIFIER_MAX_LENGTH) String creatorIdentifier
) {
}
