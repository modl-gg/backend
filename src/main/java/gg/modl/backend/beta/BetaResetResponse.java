package gg.modl.backend.beta;

import java.util.List;

public record BetaResetResponse(String serverId, List<String> clearedCollections) {
}
