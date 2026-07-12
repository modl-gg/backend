package gg.modl.backend.player.dto.response;

import java.util.List;
import java.util.Map;

public sealed interface LinkedAccountsResult permits LinkedAccountsResult.Found, LinkedAccountsResult.NotFound {

    record Found(
        List<Map<String, Object>> linkedAccounts,
        Integer totalCount,
        Integer page,
        Boolean hasMore
    ) implements LinkedAccountsResult {
    }

    record NotFound(String message) implements LinkedAccountsResult {
    }
}
