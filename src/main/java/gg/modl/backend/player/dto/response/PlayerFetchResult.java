package gg.modl.backend.player.dto.response;

import java.util.Map;

public sealed interface PlayerFetchResult
    permits PlayerFetchResult.Found, PlayerFetchResult.NotFound, PlayerFetchResult.InvalidRequest {

    record Found(String message, Map<String, Object> player) implements PlayerFetchResult {
    }

    record NotFound(String message) implements PlayerFetchResult {
    }

    record InvalidRequest(String message) implements PlayerFetchResult {
    }
}
