package gg.modl.backend.player.dto.response;

import java.util.Map;

public sealed interface PlayerProfileResult permits PlayerProfileResult.Found, PlayerProfileResult.NotFound {

    record Found(Map<String, Object> profile) implements PlayerProfileResult {
    }

    record NotFound(String message) implements PlayerProfileResult {
    }
}
