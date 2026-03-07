package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DismissReportRequest(
        @Size(max = RequestValidationLimits.REPORT_STAFF_NAME_MAX_LENGTH)
        @Pattern(regexp = RequestValidationLimits.NON_BLANK_TEXT)
        String dismissedBy,

        @Size(max = RequestValidationLimits.REPORT_REASON_MAX_LENGTH)
        String reason
) {
}