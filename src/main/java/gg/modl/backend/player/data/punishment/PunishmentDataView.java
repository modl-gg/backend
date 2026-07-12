package gg.modl.backend.player.data.punishment;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public final class PunishmentDataView {
    @Nullable
    private Map<String, Object> data;
    @Nullable
    private final Consumer<Map<String, Object>> installer;

    private PunishmentDataView(@Nullable Map<String, Object> data, @Nullable Consumer<Map<String, Object>> installer) {
        this.data = data;
        this.installer = installer;
    }

    static PunishmentDataView ownedBy(@Nullable Map<String, Object> data, Consumer<Map<String, Object>> installer) {
        return new PunishmentDataView(data, installer);
    }

    public static PunishmentDataView ofMap(Map<String, Object> data) {
        return new PunishmentDataView(data, null);
    }

    @Nullable
    public Map<String, Object> asMap() {
        return data;
    }

    private Map<String, Object> mutable() {
        if (data == null) {
            data = new HashMap<>();
            if (installer != null) {
                installer.accept(data);
            }
        }
        return data;
    }

    @Nullable
    public String status() {
        return data != null && data.get(PunishmentData.STATUS) instanceof String value ? value : null;
    }

    public boolean hasStatus() {
        return data != null && data.containsKey(PunishmentData.STATUS);
    }

    public boolean isUnstarted() {
        return PunishmentStatus.UNSTARTED.equals(status());
    }

    public void setStatus(String status) {
        mutable().put(PunishmentData.STATUS, status);
    }

    public void removeStatus() {
        if (data != null) {
            data.remove(PunishmentData.STATUS);
        }
    }

    @Nullable
    public Long duration() {
        if (data == null) {
            return null;
        }
        Object value = data.get(PunishmentData.DURATION);
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public void setDuration(long duration) {
        mutable().put(PunishmentData.DURATION, duration);
    }

    @Nullable
    public String reason() {
        return data != null && data.get(PunishmentData.REASON) instanceof String value ? value : null;
    }

    public void setReason(String reason) {
        mutable().put(PunishmentData.REASON, reason);
    }

    @Nullable
    public String severity() {
        return data != null && data.get(PunishmentData.SEVERITY) instanceof String value ? value : null;
    }

    public void setSeverity(String severity) {
        mutable().put(PunishmentData.SEVERITY, severity);
    }

    @Nullable
    public String offenseLevel() {
        return data != null && data.get(PunishmentData.OFFENSE_LEVEL) instanceof String value ? value : null;
    }

    public boolean hasOffenseLevel() {
        return data != null && data.containsKey(PunishmentData.OFFENSE_LEVEL);
    }

    public void setOffenseLevel(String offenseLevel) {
        mutable().put(PunishmentData.OFFENSE_LEVEL, offenseLevel);
    }

    @Nullable
    public String enforcementCategory() {
        return data != null && data.get(PunishmentData.ENFORCEMENT_CATEGORY) instanceof String value ? value : null;
    }

    public void setEnforcementCategory(String enforcementCategory) {
        mutable().put(PunishmentData.ENFORCEMENT_CATEGORY, enforcementCategory);
    }

    public boolean altBlocking() {
        return data != null && Boolean.TRUE.equals(data.get(PunishmentData.ALT_BLOCKING));
    }

    public void setAltBlocking(boolean altBlocking) {
        mutable().put(PunishmentData.ALT_BLOCKING, altBlocking);
    }

    public boolean wipeAfterExpiry() {
        return data != null && Boolean.TRUE.equals(data.get(PunishmentData.WIPE_AFTER_EXPIRY));
    }

    public void setWipeAfterExpiry(boolean wipeAfterExpiry) {
        mutable().put(PunishmentData.WIPE_AFTER_EXPIRY, wipeAfterExpiry);
    }

    public boolean statWipeCompleted() {
        return data != null && Boolean.TRUE.equals(data.get(PunishmentData.STAT_WIPE_COMPLETED));
    }

    public void setPendingAcknowledgement(boolean pendingAcknowledgement) {
        mutable().put(PunishmentData.PENDING_ACKNOWLEDGEMENT, pendingAcknowledgement);
    }

    public boolean removePendingAcknowledgement() {
        return data != null && Boolean.TRUE.equals(data.remove(PunishmentData.PENDING_ACKNOWLEDGEMENT));
    }

    @Nullable
    public String linkedBanId() {
        return data != null && data.get(PunishmentData.LINKED_BAN_ID) instanceof String value ? value : null;
    }

    public void setLinkedBanId(String linkedBanId) {
        mutable().put(PunishmentData.LINKED_BAN_ID, linkedBanId);
    }

    public void setLinkedBanParentUuid(String linkedBanParentUuid) {
        mutable().put(PunishmentData.LINKED_BAN_PARENT_UUID, linkedBanParentUuid);
    }

    @Nullable
    public String blockedName() {
        return data != null && data.get(PunishmentData.BLOCKED_NAME) instanceof String value ? value : null;
    }

    public boolean hasBlockedName() {
        return data != null && data.containsKey(PunishmentData.BLOCKED_NAME);
    }

    public void setBlockedName(String blockedName) {
        mutable().put(PunishmentData.BLOCKED_NAME, blockedName);
    }

    @Nullable
    public String blockedSkin() {
        return data != null && data.get(PunishmentData.BLOCKED_SKIN) instanceof String value ? value : null;
    }

    public boolean hasBlockedSkin() {
        return data != null && data.containsKey(PunishmentData.BLOCKED_SKIN);
    }

    public void setBlockedSkin(String blockedSkin) {
        mutable().put(PunishmentData.BLOCKED_SKIN, blockedSkin);
    }
}
