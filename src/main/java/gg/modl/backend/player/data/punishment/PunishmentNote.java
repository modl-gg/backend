package gg.modl.backend.player.data.punishment;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

public record PunishmentNote(
        @NotNull @Field("id") String id,
        @NotNull String text,
        @NotNull Date date,
        @NotNull String issuerName
) {
}
