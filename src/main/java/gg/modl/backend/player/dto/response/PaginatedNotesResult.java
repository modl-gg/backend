package gg.modl.backend.player.dto.response;

import java.util.List;
import java.util.Map;

public sealed interface PaginatedNotesResult permits PaginatedNotesResult.Found, PaginatedNotesResult.NotFound {

    record Found(
        List<Map<String, Object>> notes,
        int totalCount,
        int page,
        boolean hasMore
    ) implements PaginatedNotesResult {
    }

    record NotFound(String message) implements PaginatedNotesResult {
    }
}
