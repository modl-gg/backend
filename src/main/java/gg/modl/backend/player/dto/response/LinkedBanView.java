package gg.modl.backend.player.dto.response;

public record LinkedBanView(
    String punishmentId,
    String playerUuid,
    String playerName,
    boolean active
) {
}
