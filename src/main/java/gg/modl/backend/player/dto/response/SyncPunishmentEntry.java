package gg.modl.backend.player.dto.response;

public record SyncPunishmentEntry(
    String minecraftUuid,
    String username,
    SimplePunishmentView punishment
) {
}
