package gg.modl.backend.player.dto.response;

import java.util.List;
import org.jetbrains.annotations.Nullable;

public record SimplePunishmentView(
    String id,
    String type,
    String category,
    int typeOrdinal,
    int ordinal,
    boolean started,
    @Nullable Long expiration,
    String description,
    String issuerName,
    long issuedAt,
    @Nullable String playerDescription,
    List<Modification> modifications
) {
    public record Modification(
        String type,
        @Nullable Long timestamp,
        long effectiveDuration,
        String issuerName
    ) {
    }
}
