package gg.modl.backend.player.data.punishment;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

public final class PunishmentData {
    private PunishmentData() {}

    @Nullable
    public static String getStatus(@Nullable Map<String, Object> data) {
        return data != null && data.get("status") instanceof String s ? s : null;
    }

    @Nullable
    public static Long getDuration(@Nullable Map<String, Object> data) {
        if (data == null) return null;
        Object v = data.get("duration");
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return null;
    }

    @Nullable
    public static String getReason(@Nullable Map<String, Object> data) {
        return data != null && data.get("reason") instanceof String s ? s : null;
    }

    @Nullable
    public static String getSeverity(@Nullable Map<String, Object> data) {
        return data != null && data.get("severity") instanceof String s ? s : null;
    }

    @Nullable
    public static String getOffenseLevel(@Nullable Map<String, Object> data) {
        return data != null && data.get("offenseLevel") instanceof String s ? s : null;
    }

    @Nullable
    public static String getEnforcementCategory(@Nullable Map<String, Object> data) {
        return data != null && data.get("enforcementCategory") instanceof String s ? s : null;
    }

    public static boolean isAltBlocking(@Nullable Map<String, Object> data) {
        return Boolean.TRUE.equals(data != null ? data.get("altBlocking") : null);
    }

    public static boolean isWipeAfterExpiry(@Nullable Map<String, Object> data) {
        return Boolean.TRUE.equals(data != null ? data.get("wipeAfterExpiry") : null);
    }

    public static boolean isStatWipeCompleted(@Nullable Map<String, Object> data) {
        return Boolean.TRUE.equals(data != null ? data.get("statWipeCompleted") : null);
    }

    public static boolean isPendingAcknowledgement(@Nullable Map<String, Object> data) {
        return Boolean.TRUE.equals(data != null ? data.get("pendingAcknowledgement") : null);
    }

    @Nullable
    public static String getLinkedBanId(@Nullable Map<String, Object> data) {
        return data != null && data.get("linkedBanId") instanceof String s ? s : null;
    }

    @Nullable
    public static String getLinkedBanParentUuid(@Nullable Map<String, Object> data) {
        return data != null && data.get("linkedBanParentUuid") instanceof String s ? s : null;
    }

    @Nullable
    public static String getBlockedName(@Nullable Map<String, Object> data) {
        return data != null && data.get("blockedName") instanceof String s ? s : null;
    }

    @Nullable
    public static String getBlockedSkin(@Nullable Map<String, Object> data) {
        return data != null && data.get("blockedSkin") instanceof String s ? s : null;
    }
}
