package gg.modl.backend.player.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_PLAYERS)
@RequiredArgsConstructor
public class MinecraftSyncController {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @RequestBody SyncRequest syncRequest,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Instant now = Instant.now();
        Instant lastSync = syncRequest.lastSyncTimestamp() != null
                ? Instant.parse(syncRequest.lastSyncTimestamp())
                : now.minusSeconds(30);

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        List<Map<String, Object>> pendingPunishments = new ArrayList<>();
        List<Map<String, Object>> recentlyModifiedPunishments = new ArrayList<>();
        List<Map<String, Object>> playerNotifications = new ArrayList<>();

        Set<String> onlineUuids = new HashSet<>();
        if (syncRequest.onlinePlayers() != null) {
            for (OnlinePlayer op : syncRequest.onlinePlayers()) {
                if (op.uuid() != null) {
                    onlineUuids.add(op.uuid());
                }
            }
        }

        if (!onlineUuids.isEmpty()) {
            Query playerQuery = Query.query(Criteria.where("minecraftUuid").in(onlineUuids));
            List<Player> players = template.find(playerQuery, Player.class, CollectionName.PLAYERS);

            for (Player player : players) {
                String uuid = player.getMinecraftUuid().toString();
                String username = player.getUsernames().isEmpty() ? "Unknown"
                        : player.getUsernames().get(player.getUsernames().size() - 1).username();

                for (Punishment punishment : player.getPunishments()) {
                    boolean isActive = statusCalculator.isPunishmentActive(punishment);

                    // Check for recent modifications (including pardons on now-inactive punishments)
                    boolean recentlyModified = punishment.getModifications().stream()
                            .anyMatch(m -> m.date() != null && m.date().toInstant().isAfter(lastSync));

                    // Include recently modified punishments even if inactive (e.g., pardons)
                    if (recentlyModified) {
                        Map<String, Object> simplePunishment = toSimplePunishment(punishment, types);
                        recentlyModifiedPunishments.add(Map.of(
                                "minecraftUuid", uuid,
                                "username", username,
                                "punishment", simplePunishment
                        ));
                    }

                    // For pending/new punishments, only include active ones
                    if (!isActive) continue;

                    boolean notStarted = punishment.getStarted() == null;
                    Map<String, Object> simplePunishment = toSimplePunishment(punishment, types);

                    if (notStarted) {
                        pendingPunishments.add(Map.of(
                                "minecraftUuid", uuid,
                                "username", username,
                                "punishment", simplePunishment
                        ));
                    }
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pending = (List<Map<String, Object>>) player.getData().get("pendingNotifications");
                if (pending != null) {
                    for (Map<String, Object> notification : pending) {
                        Map<String, Object> notif = new HashMap<>(notification);
                        notif.put("targetPlayerUuid", uuid);
                        playerNotifications.add(notif);
                    }
                }
            }
        }

        List<Map<String, Object>> activeStaffMembers = getActiveStaffMembers(template);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pendingPunishments", pendingPunishments);
        data.put("recentlyStartedPunishments", List.of());
        data.put("recentlyModifiedPunishments", recentlyModifiedPunishments);
        data.put("playerNotifications", playerNotifications);
        data.put("activeStaffMembers", activeStaffMembers);
        data.put("staffPermissionsUpdatedAt", server.getStaffPermissionsUpdatedAt() != null
                ? server.getStaffPermissionsUpdatedAt().getTime() : null);
        data.put("punishmentTypesUpdatedAt", server.getPunishmentTypesUpdatedAt() != null
                ? server.getPunishmentTypesUpdatedAt().getTime() : null);

        return ResponseEntity.ok(Map.of(
                "timestamp", now.toString(),
                "data", data
        ));
    }

    private Map<String, Object> toSimplePunishment(Punishment punishment, List<PunishmentType> types) {
        Map<String, Object> data = punishment.getData();
        Date expires = statusCalculator.getEffectiveExpiry(punishment);

        PunishmentType punishmentType = types.stream()
                .filter(t -> t.getOrdinal() == punishment.getType_ordinal())
                .findFirst()
                .orElse(null);

        String typeName = punishmentType != null ? punishmentType.getName() : "Unknown";
        boolean isBan = punishmentType != null && punishmentType.isBan();
        boolean isMute = punishmentType != null && punishmentType.isMute();
        String category = isBan ? "BAN" : (isMute ? "MUTE" : "OTHER");
        String playerDescription = punishmentType != null ? punishmentType.getPlayerDescription() : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", punishment.getId());
        result.put("type", typeName);
        result.put("category", category);
        result.put("ordinal", punishment.getType_ordinal());
        result.put("started", punishment.getStarted() != null);
        result.put("expiration", expires != null ? expires.getTime() : null);
        result.put("permanent", expires == null);
        result.put("reason", data != null ? data.get("reason") : null);
        result.put("issuerName", punishment.getIssuerName());
        result.put("issuedAt", punishment.getIssued().getTime());
        result.put("playerDescription", playerDescription);
        result.put("modifications", punishment.getModifications().stream().map(m -> Map.of(
                "type", m.type(),
                "timestamp", m.date() != null ? m.date().getTime() : null,
                "effectiveDuration", m.effectiveDuration() != null ? m.effectiveDuration() : 0L
        )).toList());

        return result;
    }

    private List<Map<String, Object>> getActiveStaffMembers(MongoTemplate template) {
        Query staffQuery = Query.query(
                Criteria.where("assignedMinecraftUuid").exists(true).ne(null).ne("")
        );
        List<Staff> staffWithMinecraft = template.find(staffQuery, Staff.class, CollectionName.STAFF);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Staff staff : staffWithMinecraft) {
            Query roleQuery = Query.query(Criteria.where("name").is(staff.getRole()));
            StaffRole role = template.findOne(roleQuery, StaffRole.class, CollectionName.STAFF_ROLES);

            List<String> permissions = role != null ? role.getPermissions() : List.of();

            result.add(Map.of(
                    "minecraftUuid", staff.getAssignedMinecraftUuid(),
                    "minecraftUsername", staff.getAssignedMinecraftUsername() != null ? staff.getAssignedMinecraftUsername() : "",
                    "staffUsername", staff.getUsername() != null ? staff.getUsername() : "",
                    "staffRole", staff.getRole() != null ? staff.getRole() : "",
                    "permissions", permissions,
                    "email", staff.getEmail() != null ? staff.getEmail() : ""
            ));
        }

        return result;
    }

    public record SyncRequest(
            String lastSyncTimestamp,
            List<OnlinePlayer> onlinePlayers,
            ServerStatus serverStatus
    ) {}

    public record OnlinePlayer(
            String uuid,
            String username,
            String ipAddress
    ) {}

    public record ServerStatus(
            int onlinePlayerCount,
            int maxPlayers,
            String serverVersion,
            String timestamp
    ) {}
}
