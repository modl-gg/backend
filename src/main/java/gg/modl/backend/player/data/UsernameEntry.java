package gg.modl.backend.player.data;


import java.util.Date;
import org.jetbrains.annotations.NotNull;

public record UsernameEntry(@NotNull String username, @NotNull Date date) {}