package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyTicketCodeRequest(
    @NotBlank(message = "Code is required")
    @Size(max = RequestValidationLimits.TICKET_VERIFY_CODE_MAX_LENGTH)
    String code
) {}
