package gg.modl.backend.player.data.punishment;

import org.jetbrains.annotations.NotNull;

import java.util.Date;

public record PunishmentNote(@NotNull String text, @NotNull Date date, @NotNull String issuerName) {
}
