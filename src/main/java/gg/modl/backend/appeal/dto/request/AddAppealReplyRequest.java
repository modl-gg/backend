package gg.modl.backend.appeal.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.lang.Nullable;

public record AddAppealReplyRequest(
    @NotBlank
    @Size(max = RequestValidationLimits.APPEAL_REPLY_NAME_MAX_LENGTH)
    String name,
    @NotBlank
    @Size(max = RequestValidationLimits.APPEAL_REPLY_CONTENT_MAX_LENGTH)
    String content,
    @NotBlank
    @Size(max = RequestValidationLimits.APPEAL_REPLY_TYPE_MAX_LENGTH)
    String type,
    boolean staff,
    @Nullable @Size(max = RequestValidationLimits.APPEAL_REPLY_ACTION_MAX_LENGTH) String action,
    @Nullable @Size(max = RequestValidationLimits.APPEAL_AVATAR_MAX_LENGTH) String avatar,
    @Nullable @Size(max = RequestValidationLimits.APPEAL_ATTACHMENTS_MAX_ENTRIES) List<Object> attachments
) {
}
