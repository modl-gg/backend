package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignReportRequest(
    @NotBlank
    @Size(max = RequestValidationLimits.REPORT_ASSIGNEE_MAX_LENGTH)
    String assignee
) {
}