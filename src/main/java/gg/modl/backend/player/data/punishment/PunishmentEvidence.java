package gg.modl.backend.player.data.punishment;

import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PunishmentEvidence(
    @Nullable String text,
    @Nullable String url,
    @NotNull String type,
    @Nullable String uploadedBy,
    @Nullable String uploadedById,
    @NotNull Date uploadedAt,
    @Nullable String fileName,
    @Nullable String fileType,
    @Nullable Long fileSize
) {
}
