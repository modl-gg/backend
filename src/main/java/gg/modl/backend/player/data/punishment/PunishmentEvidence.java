package gg.modl.backend.player.data.punishment;

import org.jetbrains.annotations.NotNull;

import java.util.Date;

public record PunishmentEvidence(@NotNull String url, @NotNull String type, @NotNull String uploadedBy, @NotNull Date uploadedAt) {
}
