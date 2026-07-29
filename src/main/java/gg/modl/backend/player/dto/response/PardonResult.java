package gg.modl.backend.player.dto.response;

public sealed interface PardonResult permits PardonResult.Pardoned, PardonResult.PlayerNotFound {

    record Pardoned(boolean success, int pardonedCount, String message) implements PardonResult {
    }

    record PlayerNotFound(String message) implements PardonResult {
    }
}
