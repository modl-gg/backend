package gg.modl.backend.player.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddPunishmentNoteRequest(
        @NotBlank String text,
        @NotBlank String issuerName
) {
}
