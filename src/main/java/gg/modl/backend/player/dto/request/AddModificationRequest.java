package gg.modl.backend.player.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.Nullable;

public record AddModificationRequest(
        @NotBlank String type,
        @NotBlank String issuerName,
        @Nullable Long effectiveDuration,
        @Nullable String reason,
        @Nullable String appealTicketId
) {
}
