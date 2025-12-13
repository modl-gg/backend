package gg.modl.backend.player.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public record IPEntry(@NotNull String ipAddress, @Nullable String country, @Nullable String region, @Nullable String asn,
                      boolean proxy, boolean hosting, @NotNull Date firstLogin) {
}
