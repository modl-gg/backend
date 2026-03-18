package gg.modl.backend.knowledgebase.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReorderRequest(
    @NotEmpty
    @Size(max = RequestValidationLimits.KB_REORDER_MAX_IDS)
    List<@NotBlank @Size(max = RequestValidationLimits.NOTIFICATION_ID_MAX_LENGTH) String> ids
) {
}
