package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyTicketCodeRequest(
    @NotBlank(message = "Code is required")
    String code
) {}
