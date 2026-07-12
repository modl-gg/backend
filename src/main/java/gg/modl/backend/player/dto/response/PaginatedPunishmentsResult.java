package gg.modl.backend.player.dto.response;

import java.util.List;

public sealed interface PaginatedPunishmentsResult
    permits PaginatedPunishmentsResult.Found, PaginatedPunishmentsResult.NotFound {

    record Found(
        List<PunishmentView> punishments,
        int totalCount,
        int page,
        boolean hasMore
    ) implements PaginatedPunishmentsResult {
    }

    record NotFound(String message) implements PaginatedPunishmentsResult {
    }
}
