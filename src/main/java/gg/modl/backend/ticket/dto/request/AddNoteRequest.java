package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddNoteRequest(
        @NotBlank String text,
        @NotBlank String issuerName,
        String issuerAvatar
) {
}
