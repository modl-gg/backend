package gg.modl.backend.player.dto.response;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public record SyncDataView(
    List<SyncPunishmentEntry> pendingPunishments,
    List<SyncPunishmentEntry> recentlyStartedPunishments,
    List<SyncPunishmentEntry> recentlyModifiedPunishments,
    List<Map<String, Object>> playerNotifications,
    List<Map<String, Object>> staffNotifications,
    List<Map<String, Object>> activeStaffMembers,
    List<Map<String, Object>> pendingStatWipes,
    @Nullable Long staffPermissionsUpdatedAt,
    @Nullable Long punishmentTypesUpdatedAt,
    @Nullable List<Map<String, Object>> staff2faVerifications,
    @Nullable Map<String, Object> migrationTask
) {
}
