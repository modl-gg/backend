package gg.modl.backend.player.dto.response;

public record SyncResult(
    String timestamp,
    SyncDataView data
) {
}
