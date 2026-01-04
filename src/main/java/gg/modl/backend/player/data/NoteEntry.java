package gg.modl.backend.player.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public record NoteEntry(
        @NotNull String id,
        @NotNull String text,
        @NotNull Date date,
        @NotNull String issuerName,
        @Nullable String issuerId
) {
}
