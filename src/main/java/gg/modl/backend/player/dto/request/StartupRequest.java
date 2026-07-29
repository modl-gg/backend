package gg.modl.backend.player.dto.request;

public record StartupRequest(
    String serverVersion,
    String platformType,
    String pluginVersion,
    int maxPlayers,
    String serverName
) {
}
