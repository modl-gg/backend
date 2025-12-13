package gg.modl.backend.player.data;

import org.jetbrains.annotations.NotNull;

import java.util.Date;

public record NoteEntry(@NotNull String text, @NotNull Date addedAt, @NotNull String issuerName) {
}
