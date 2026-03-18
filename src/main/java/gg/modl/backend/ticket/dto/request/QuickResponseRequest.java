package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.lang.Nullable;

public record QuickResponseRequest(
    @NotBlank @Size(max = RequestValidationLimits.QUICK_RESPONSE_ACTION_ID_MAX_LENGTH) String actionId,
    @NotBlank @Size(max = RequestValidationLimits.QUICK_RESPONSE_CATEGORY_ID_MAX_LENGTH) String categoryId,
    @Nullable @PositiveOrZero Integer punishmentTypeId,
    @Nullable @Size(max = RequestValidationLimits.QUICK_RESPONSE_SEVERITY_MAX_LENGTH) String punishmentSeverity,
    @Nullable @Size(max = RequestValidationLimits.QUICK_RESPONSE_CUSTOM_VALUES_MAX_ENTRIES) Map<String, Object> customValues,
    @Nullable @Size(max = RequestValidationLimits.QUICK_RESPONSE_APPEAL_ACTION_MAX_LENGTH) String appealAction
) {
}
