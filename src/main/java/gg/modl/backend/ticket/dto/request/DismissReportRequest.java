package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record DismissReportRequest(
    @Size(max = RequestValidationLimits.REPORT_STAFF_NAME_MAX_LENGTH)
    @NotBlank
    String dismissedBy,

    @Nullable
    @Size(max = RequestValidationLimits.REPORT_REASON_MAX_LENGTH)
    String reason
) {
}
