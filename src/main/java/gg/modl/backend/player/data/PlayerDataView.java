package gg.modl.backend.player.data;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public final class PlayerDataView {
    @Nullable
    private Map<String, Object> data;
    @Nullable
    private final Consumer<Map<String, Object>> installer;

    private PlayerDataView(@Nullable Map<String, Object> data, @Nullable Consumer<Map<String, Object>> installer) {
        this.data = data;
        this.installer = installer;
    }

    static PlayerDataView ownedBy(@Nullable Map<String, Object> data, Consumer<Map<String, Object>> installer) {
        return new PlayerDataView(data, installer);
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

    public boolean isOnline() {
        return data != null && Boolean.TRUE.equals(data.get(PlayerDataKeys.IS_ONLINE));
    }

    public void setOnline(boolean online) {
        mutable().put(PlayerDataKeys.IS_ONLINE, online);
    }

    @Nullable
    public Date lastLogin() {
        return data != null && data.get(PlayerDataKeys.LAST_LOGIN) instanceof Date value ? value : null;
    }

    public void setLastLogin(Date lastLogin) {
        mutable().put(PlayerDataKeys.LAST_LOGIN, lastLogin);
    }

    @Nullable
    public String lastServer() {
        return data != null && data.get(PlayerDataKeys.LAST_SERVER) instanceof String value ? value : null;
    }

    public void setLastServer(String lastServer) {
        mutable().put(PlayerDataKeys.LAST_SERVER, lastServer);
    }

    @Nullable
    public Number totalPlaytimeSeconds() {
        return data != null && data.get(PlayerDataKeys.TOTAL_PLAYTIME_SECONDS) instanceof Number value ? value : null;
    }

    @Nullable
    public String lastSkinHash() {
        return data != null && data.get(PlayerDataKeys.LAST_SKIN_HASH) instanceof String value ? value : null;
    }

    public void setLastSkinHash(String lastSkinHash) {
        mutable().put(PlayerDataKeys.LAST_SKIN_HASH, lastSkinHash);
    }

    @Nullable
    public Date firstJoin() {
        return data != null && data.get(PlayerDataKeys.FIRST_JOIN) instanceof Date value ? value : null;
    }

    public boolean hasFirstJoin() {
        return data != null && data.containsKey(PlayerDataKeys.FIRST_JOIN);
    }

    public void setFirstJoin(Date firstJoin) {
        mutable().put(PlayerDataKeys.FIRST_JOIN, firstJoin);
    }

    @Nullable
    public Date lastLinkedUpdate() {
        return data != null && data.get(PlayerDataKeys.LAST_LINKED_UPDATE) instanceof Date value ? value : null;
    }

    public boolean hasLinkedAccounts() {
        return data != null && data.containsKey(PlayerDataKeys.LINKED_ACCOUNTS);
    }

    @Nullable
    public Object linkedAccountsValue() {
        return data != null ? data.get(PlayerDataKeys.LINKED_ACCOUNTS) : null;
    }

    public List<String> linkedAccountUuids() {
        if (data == null || !(data.get(PlayerDataKeys.LINKED_ACCOUNTS) instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .toList();
    }

    public List<Map<String, Object>> pendingNotifications() {
        if (data == null || !(data.get(PlayerDataKeys.PENDING_NOTIFICATIONS) instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(Map.class::isInstance)
            .map(entry -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> notification = (Map<String, Object>) entry;
                return notification;
            })
            .toList();
    }

    public void setPendingNotifications(List<Map<String, Object>> pendingNotifications) {
        mutable().put(PlayerDataKeys.PENDING_NOTIFICATIONS, pendingNotifications);
    }
}
