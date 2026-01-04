package gg.modl.backend.player.data.punishment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public record PunishmentModification(
        @NotNull String type,
        @NotNull Date date,
        @NotNull String issuerName,
        @NotNull String reason,
        @Nullable Long effectiveDuration,
        @Nullable String appealTicketId
) {
}
