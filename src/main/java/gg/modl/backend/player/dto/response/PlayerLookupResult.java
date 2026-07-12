package gg.modl.backend.player.dto.response;

import java.util.Map;

public sealed interface PlayerLookupResult permits PlayerLookupResult.Found, PlayerLookupResult.NotFound {

    record Found(String message, Map<String, Object> data) implements PlayerLookupResult {
    }

    record NotFound(String message) implements PlayerLookupResult {
    }
}
