package gg.modl.backend.player.data.punishment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public record PunishmentEvidence(
        @Nullable String text,
        @Nullable String url,
        @NotNull String type,
        @NotNull String uploadedBy,
        @NotNull Date uploadedAt,
        @Nullable String fileName,
        @Nullable String fileType,
        @Nullable Long fileSize
) {
}
