package gg.modl.backend.appeal.dto.request;

import gg.modl.backend.infrastructure.validation.RegExpConstants;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

public record CreateAppealRequest(
    @NotBlank @Size(max = RequestValidationLimits.REPORT_PUNISHMENT_ID_MAX_LENGTH) String punishmentId,
    @NotBlank @Pattern(regexp = RegExpConstants.UUID) String playerUuid,
    @NotBlank @Email @Size(max = RequestValidationLimits.EMAIL_MAX_LENGTH) String email,
    @Nullable @Size(max = RequestValidationLimits.APPEAL_REASON_MAX_LENGTH) String reason,
    @Nullable @Size(max = RequestValidationLimits.APPEAL_EVIDENCE_MAX_LENGTH) String evidence,
    @Nullable @Size(max = RequestValidationLimits.APPEAL_ADDITIONAL_DATA_MAX_ENTRIES) Map<String, Object> additionalData,
    @Nullable @Size(max = RequestValidationLimits.APPEAL_ATTACHMENTS_MAX_ENTRIES) List<Object> attachments,
    @Nullable @Size(max = RequestValidationLimits.APPEAL_FIELD_LABELS_MAX_ENTRIES) Map<String, String> fieldLabels
) {
}
