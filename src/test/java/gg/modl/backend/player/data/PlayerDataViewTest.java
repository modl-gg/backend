package gg.modl.backend.player.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlayerDataViewTest {

    @Test
    void mutatingOneFieldPreservesUnknownAndUntouchedKeys() {
        Player player = Player.builder().build();
        player.data().setOnline(true);
        player.data().asMap().put(PlayerDataKeys.LAST_SERVER, "hub");
        player.data().asMap().put("legacyPluginField", "keep-me");

        player.data().setLastServer("lobby");

        Map<String, Object> stored = player.data().asMap();
        assertEquals(true, stored.get(PlayerDataKeys.IS_ONLINE));
        assertEquals("lobby", stored.get(PlayerDataKeys.LAST_SERVER));
        assertEquals("keep-me", stored.get("legacyPluginField"));
        assertEquals(3, stored.size());
    }

    @Test
    void readsAreNullSafeWhenDataAbsent() {
        Player player = Player.builder().build();
        player.replaceData(null);

        assertFalse(player.data().isOnline());
        assertNull(player.data().lastLogin());
        assertNull(player.data().lastServer());
        assertNull(player.data().totalPlaytimeSeconds());
        assertTrue(player.data().linkedAccountUuids().isEmpty());
        assertTrue(player.data().pendingNotifications().isEmpty());
        assertFalse(player.data().hasLinkedAccounts());
        assertNull(player.data().asMap());
    }

    @Test
    void linkedAccountUuidsFilterNonStringEntries() {
        Player player = Player.builder().build();
        player.data().asMap().put(PlayerDataKeys.LINKED_ACCOUNTS, Arrays.asList("alt-a", 7, null, "alt-b"));

        assertEquals(List.of("alt-a", "alt-b"), player.data().linkedAccountUuids());
        assertTrue(player.data().hasLinkedAccounts());
    }

    @Test
    void pendingNotificationsFilterNonMapEntries() {
        Player player = Player.builder().build();
        List<Object> raw = new ArrayList<>();
        raw.add(Map.of("id", "n1"));
        raw.add("not-a-map");
        raw.add(Map.of("id", "n2"));
        player.data().asMap().put(PlayerDataKeys.PENDING_NOTIFICATIONS, raw);

        List<Map<String, Object>> notifications = player.data().pendingNotifications();
        assertEquals(2, notifications.size());
        assertEquals("n1", notifications.get(0).get("id"));
        assertEquals("n2", notifications.get(1).get("id"));
    }

    @Test
    void wrongTypedLegacyValuesAreTolerated() {
        Player player = Player.builder().build();
        player.data().asMap().put(PlayerDataKeys.TOTAL_PLAYTIME_SECONDS, "lots");
        player.data().asMap().put(PlayerDataKeys.LAST_LOGIN, "yesterday");
        player.data().asMap().put(PlayerDataKeys.IS_ONLINE, "yes");

        assertNull(player.data().totalPlaytimeSeconds());
        assertNull(player.data().lastLogin());
        assertFalse(player.data().isOnline());
    }

    @Test
    void lastLoginReadsBackTypedDate() {
        Player player = Player.builder().build();
        Date now = new Date();
        player.data().setLastLogin(now);

        assertEquals(now, player.data().lastLogin());
    }
}
