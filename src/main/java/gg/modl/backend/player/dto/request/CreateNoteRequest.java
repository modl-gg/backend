package gg.modl.backend.player.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.Nullable;

public record CreateNoteRequest(
        @NotBlank String text,
        @Nullable String issuerName,
        @Nullable String date
) {
}
