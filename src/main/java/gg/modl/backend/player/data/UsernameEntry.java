package gg.modl.backend.player.data;


import org.jetbrains.annotations.NotNull;

import java.util.Date;

public record UsernameEntry(@NotNull String username, @NotNull Date addedAt) {}