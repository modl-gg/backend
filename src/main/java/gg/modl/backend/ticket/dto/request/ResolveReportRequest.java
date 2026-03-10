package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveReportRequest(
        @Size(max = RequestValidationLimits.REPORT_STAFF_NAME_MAX_LENGTH)
        @NotBlank
        String resolvedBy,

        @Size(max = RequestValidationLimits.REPORT_REASON_MAX_LENGTH)
        String resolution,

        @Size(max = RequestValidationLimits.REPORT_PUNISHMENT_ID_MAX_LENGTH)
        @NotBlank
        String punishmentId
) {
}