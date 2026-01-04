package gg.modl.backend.player.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.Nullable;

public record AddNoteRequest(
        @NotBlank String text,
        @NotBlank String issuerName,
        @Nullable String issuerId
) {
}
