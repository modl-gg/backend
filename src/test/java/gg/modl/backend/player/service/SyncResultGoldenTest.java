package gg.modl.backend.player.service;
import gg.modl.backend.player.dto.response.SimplePunishmentView;
import gg.modl.backend.player.dto.response.SyncDataView;
import gg.modl.backend.player.dto.response.SyncPunishmentEntry;
import gg.modl.backend.player.dto.response.SyncResult;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SyncResultGoldenTest {

    private static final ObjectMapper JSON = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    void syncResultWithOptionalSectionsIsTreeIdenticalToLegacyMap() throws Exception {
        SimplePunishmentView punishment = new SimplePunishmentView(
            "punishment-1", "Ban", "BAN", 2, 2, true, 1_800_000_000_000L,
            "Rule violation", "Mod", 1_600_000_000_000L, "You are banned",
            List.of(new SimplePunishmentView.Modification("MANUAL_PARDON", 1_650_000_000_000L, 3_600_000L, "Admin")));

        Map<String, Object> staffNotification = new LinkedHashMap<>();
        staffNotification.put("id", "ticket_1");
        staffNotification.put("type", "TICKET_CREATED");
        staffNotification.put("message", "created");
        staffNotification.put("timestamp", 1_600_000_000_000L);
        staffNotification.put("data", Map.of("ticketId", "1", "ticketUrl", "https://demo.modl.gg/ticket/1"));

        SyncResult view = new SyncResult("2026-01-01T00:00:00Z", new SyncDataView(
            List.of(new SyncPunishmentEntry("uuid-1", "Byteful", punishment)),
            List.of(),
            List.of(),
            List.of(),
            List.of(staffNotification),
            List.of(),
            List.of(),
            1_700_000_000_000L,
            1_700_000_001_000L,
            List.of(Map.of("minecraftUuid", "staff-uuid")),
            Map.of("taskId", "task-1", "type", "IP_IMPORT")));

        Map<String, Object> legacyData = new LinkedHashMap<>();
        legacyData.put("pendingPunishments", List.of(Map.of("minecraftUuid", "uuid-1", "username", "Byteful", "punishment", punishment)));
        legacyData.put("recentlyStartedPunishments", List.of());
        legacyData.put("recentlyModifiedPunishments", List.of());
        legacyData.put("playerNotifications", List.of());
        legacyData.put("staffNotifications", List.of(staffNotification));
        legacyData.put("activeStaffMembers", List.of());
        legacyData.put("pendingStatWipes", List.of());
        legacyData.put("staffPermissionsUpdatedAt", 1_700_000_000_000L);
        legacyData.put("punishmentTypesUpdatedAt", 1_700_000_001_000L);
        legacyData.put("staff2faVerifications", List.of(Map.of("minecraftUuid", "staff-uuid")));
        legacyData.put("migrationTask", Map.of("taskId", "task-1", "type", "IP_IMPORT"));
        Map<String, Object> legacy = Map.of("timestamp", "2026-01-01T00:00:00Z", "data", legacyData);

        assertEquals(JSON.readTree(JSON.writeValueAsString(legacy)), JSON.readTree(JSON.writeValueAsString(view)));
    }

    @Test
    void syncResultWithAbsentOptionalSectionsOmitsThoseKeys() throws Exception {
        SyncResult view = new SyncResult("2026-01-01T00:00:00Z", new SyncDataView(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            null, null, null, null));

        Map<String, Object> legacyData = new LinkedHashMap<>();
        legacyData.put("pendingPunishments", List.of());
        legacyData.put("recentlyStartedPunishments", List.of());
        legacyData.put("recentlyModifiedPunishments", List.of());
        legacyData.put("playerNotifications", List.of());
        legacyData.put("staffNotifications", List.of());
        legacyData.put("activeStaffMembers", List.of());
        legacyData.put("pendingStatWipes", List.of());
        Map<String, Object> legacy = Map.of("timestamp", "2026-01-01T00:00:00Z", "data", legacyData);

        assertEquals(JSON.readTree(JSON.writeValueAsString(legacy)), JSON.readTree(JSON.writeValueAsString(view)));
    }
}
