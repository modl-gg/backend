package gg.modl.backend.player.data.punishment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.Map;

public record PunishmentModification(
        @NotNull @Field("_id") String id,
        @NotNull String type,
        @NotNull Date date,
        @NotNull String issuerName,
        @NotNull String reason,
        @Nullable Long effectiveDuration,
        @Nullable String appealTicketId,
        @Nullable Map<String, Object> data
) {
}
