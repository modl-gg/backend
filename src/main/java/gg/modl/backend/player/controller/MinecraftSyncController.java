package gg.modl.backend.player.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.player.service.PunishmentMapper;
import gg.modl.backend.player.service.PunishmentService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.validation.RegExpConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
@Slf4j
public class MinecraftSyncController {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final PunishmentService punishmentService;

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @RequestBody @Valid SyncRequest syncRequest,
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

        // Reconcile online status: mark players as offline if not in the plugin's online list
        Query staleOnlineQuery = Query.query(
                Criteria.where("data.isOnline").is(true)
                        .and("minecraftUuid").nin(onlineUuids)
        );
        Update markOffline = new Update()
                .set("data.isOnline", false)
                .set("data.lastLogout", Date.from(now));
        template.updateMulti(staleOnlineQuery, markOffline, Player.class, CollectionName.PLAYERS);

        if (!onlineUuids.isEmpty()) {
            Query playerQuery = Query.query(Criteria.where("minecraftUuid").in(onlineUuids));
            List<Player> players = template.find(playerQuery, Player.class, CollectionName.PLAYERS);

            for (Player player : players) {
                // Promote queued punishments if previous ones expired or were pardoned
                List<String> promoted = punishmentService.promoteUnstartedPunishments(server, player);
                if (!promoted.isEmpty()) {
                    // Re-fetch player to get updated punishment data
                    Query refetchQuery = Query.query(Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString()));
                    player = template.findOne(refetchQuery, Player.class, CollectionName.PLAYERS);
                    if (player == null) continue;
                }

                String uuid = player.getMinecraftUuid().toString();
                String username = player.getUsernames().isEmpty() ? "Unknown"
                        : player.getUsernames().get(player.getUsernames().size() - 1).username();

                // Determine which categories already have an active punishment (started or not)
                // and find the oldest unstarted per category to send as pending
                Set<String> categoriesWithActiveStarted = new HashSet<>();
                Map<String, Punishment> oldestUnstartedPerCategory = new LinkedHashMap<>();

                for (Punishment p : player.getPunishments()) {
                    boolean isActive = statusCalculator.isPunishmentActive(p);
                    String category = statusCalculator.getEffectiveCategory(p, types);

                    if (!isActive) continue;

                    if (category != null && p.getStarted() != null) {
                        categoriesWithActiveStarted.add(category);
                    } else if (category != null && p.getStarted() == null) {
                        Punishment existing = oldestUnstartedPerCategory.get(category);
                        if (existing == null || p.getIssued().before(existing.getIssued())) {
                            oldestUnstartedPerCategory.put(category, p);
                        }
                    }
                }

                for (Punishment punishment : player.getPunishments()) {
                    boolean isActive = statusCalculator.isPunishmentActive(punishment);

                    // Check for recent modifications (including pardons on now-inactive punishments)
                    boolean recentlyModified = punishment.getModifications().stream()
                            .anyMatch(m -> m.date() != null && m.date().toInstant().isAfter(lastSync));

                    // Include recently modified punishments even if inactive (e.g., pardons)
                    if (recentlyModified) {
                        Map<String, Object> simplePunishment = PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator);
                        recentlyModifiedPunishments.add(Map.of(
                                "minecraftUuid", uuid,
                                "username", username,
                                "punishment", simplePunishment
                        ));
                    }

                    // For pending/new punishments, only include active unstarted ones
                    if (!isActive || punishment.getStarted() != null) continue;

                    String category = statusCalculator.getEffectiveCategory(punishment, types);

                    if (category != null) {
                        // Don't send if there's already an active started punishment in this category
                        if (categoriesWithActiveStarted.contains(category)) {
                            continue;
                        }
                        // Only send the oldest unstarted per category
                        if (oldestUnstartedPerCategory.get(category) != punishment) {
                            continue;
                        }
                    }

                    Map<String, Object> simplePunishment = PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator);
                    pendingPunishments.add(Map.of(
                            "minecraftUuid", uuid,
                            "username", username,
                            "punishment", simplePunishment
                    ));
                }

                // Include recently-issued, already-started punishments that need enforcement
                // on online players. This covers punishments created from the panel (which set
                // started=now immediately) that the plugin has never executed.
                for (Punishment punishment : player.getPunishments()) {
                    if (!statusCalculator.isPunishmentActive(punishment)) continue;
                    if (punishment.getStarted() == null) continue; // Handled above as unstarted
                    if (punishment.getIssued() == null || !punishment.getIssued().toInstant().isAfter(lastSync)) continue;

                    String category = statusCalculator.getEffectiveCategory(punishment, types);
                    if (category == null) continue; // Not a ban or mute â€” no enforcement needed

                    Map<String, Object> simplePunishment = PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator);
                    pendingPunishments.add(Map.of(
                            "minecraftUuid", uuid,
                            "username", username,
                            "punishment", simplePunishment
                    ));
                }

                // Include unexecuted kicks (ordinal 0) that haven't been acknowledged yet.
                // Kicks bypass the active/category system â€” they're one-time actions.
                // started == null means the plugin hasn't executed the kick yet.
                for (Punishment punishment : player.getPunishments()) {
                    if (punishment.getTypeOrdinal() != 0) continue;
                    if (punishment.getStarted() != null) continue; // Already executed

                    Map<String, Object> simplePunishment = PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator);
                    pendingPunishments.add(Map.of(
                            "minecraftUuid", uuid,
                            "username", username,
                            "punishment", simplePunishment
                    ));
                }

                // When a punishment is pardoned, re-send another active+started punishment in the
                // same category so the plugin re-enforces it (e.g. mute B after mute A is pardoned)
                Set<String> pardonedCategories = new HashSet<>();
                for (Punishment punishment : player.getPunishments()) {
                    boolean recentlyPardoned = punishment.getModifications().stream()
                            .anyMatch(m -> m.date() != null && m.date().toInstant().isAfter(lastSync)
                                    && ("MANUAL_PARDON".equals(m.type()) || "APPEAL_ACCEPT".equals(m.type()) || "SYSTEM_PARDON".equals(m.type())));
                    if (recentlyPardoned) {
                        String cat = statusCalculator.getEffectiveCategory(punishment, types);
                        if (cat != null) pardonedCategories.add(cat);
                    }
                }
                if (!pardonedCategories.isEmpty()) {
                    for (Punishment punishment : player.getPunishments()) {
                        if (!statusCalculator.isPunishmentActive(punishment) || punishment.getStarted() == null) continue;
                        String cat = statusCalculator.getEffectiveCategory(punishment, types);
                        if (cat != null && pardonedCategories.contains(cat)) {
                            Map<String, Object> simplePunishment = PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator);
                            pendingPunishments.add(Map.of(
                                    "minecraftUuid", uuid,
                                    "username", username,
                                    "punishment", simplePunishment
                            ));
                            pardonedCategories.remove(cat); // Only re-send one per category
                        }
                    }
                }

                Object rawPending = player.getData().get("pendingNotifications");
                if (rawPending instanceof List<?> pendingList) {
                    for (Object item : pendingList) {
                        if (!(item instanceof Map<?, ?> notification)) continue;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typedNotification = (Map<String, Object>) notification;
                        Map<String, Object> notif = new HashMap<>(typedNotification);
                        notif.put("targetPlayerUuid", uuid);
                        playerNotifications.add(notif);
                    }
                }
            }
        }

        // Deduplicate pending punishments: keep only oldest per player per category (BAN, MUTE)
        pendingPunishments = deduplicatePendingPunishments(pendingPunishments);

        // Generate staff notifications for recent events
        List<Map<String, Object>> staffNotifications = getRecentStaffEvents(template, lastSync, types, recentlyModifiedPunishments, server);

        List<Map<String, Object>> activeStaffMembers = getActiveStaffMembers(template);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pendingPunishments", pendingPunishments);
        data.put("recentlyStartedPunishments", List.of());
        data.put("recentlyModifiedPunishments", recentlyModifiedPunishments);
        data.put("playerNotifications", playerNotifications);
        data.put("staffNotifications", staffNotifications);
        data.put("activeStaffMembers", activeStaffMembers);
        data.put("staffPermissionsUpdatedAt", server.getStaffPermissionsUpdatedAt() != null
                ? server.getStaffPermissionsUpdatedAt().getTime() : null);
        data.put("punishmentTypesUpdatedAt", server.getPunishmentTypesUpdatedAt() != null
                ? server.getPunishmentTypesUpdatedAt().getTime() : null);

        // Check for active migration task that needs plugin action
        try {
            Query migrationQuery = Query.query(
                    Criteria.where("status").is("building_json")
            );
            MigrationStatus activeMigration = template.findOne(migrationQuery, MigrationStatus.class, "migrations");
            if (activeMigration != null) {
                data.put("migrationTask", Map.of(
                        "taskId", activeMigration.getTaskId(),
                        "type", activeMigration.getType()
                ));
            }
        } catch (Exception e) {
            // Migration collection may not exist yet, ignore
        }

        return ResponseEntity.ok(Map.of(
                "timestamp", now.toString(),
                "data", data
        ));
    }

    private List<Map<String, Object>> getRecentStaffEvents(MongoTemplate template, Instant lastSync,
                                                             List<PunishmentType> types,
                                                             List<Map<String, Object>> recentlyModifiedPunishments,
                                                             Server server) {
        List<Map<String, Object>> notifications = new ArrayList<>();

        // 1. Recent tickets created since last sync
        try {
            Query ticketQuery = Query.query(
                    Criteria.where("created").gte(Date.from(lastSync))
                            .and("status").ne("Unfinished")
            );
            ticketQuery.limit(20);
            List<Ticket> recentTickets = template.find(ticketQuery, Ticket.class, CollectionName.TICKETS);

            for (Ticket ticket : recentTickets) {
                String ticketType = ticket.getType();

                // Skip staff application tickets - no in-game notification
                if ("STAFF".equalsIgnoreCase(ticketType)) {
                    continue;
                }

                String creatorName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Unknown";
                String ticketId = ticket.getId();

                // Use the Minecraft server name from ticket data if available
                String createdServer = null;
                if (ticket.getData() != null) {
                    Object cs = ticket.getData().get("createdServer");
                    if (cs instanceof String s && !s.isBlank()) {
                        createdServer = s;
                    }
                }

                // Build message based on ticket type
                String message;
                if ("REPORT".equalsIgnoreCase(ticketType)) {
                    String reportedPlayer = ticket.getReportedPlayer() != null ? ticket.getReportedPlayer() : "Unknown";
                    String category = ticket.getCategory();
                    String categoryLabel = category != null && category.toLowerCase().contains("chat") ? "Chat" : "Gameplay";
                    message = creatorName + ": reported " + reportedPlayer;
                    if (createdServer != null) {
                        message += " on " + createdServer;
                    }
                    message += " (" + categoryLabel + ")";
                } else {
                    message = creatorName + ": created " + ticketId;
                    if (createdServer != null) {
                        message += " on " + createdServer;
                    }
                }

                Map<String, Object> notif = new LinkedHashMap<>();
                notif.put("id", "ticket_" + ticketId);
                notif.put("type", "TICKET_CREATED");
                notif.put("message", message);
                notif.put("timestamp", ticket.getCreated() != null ? ticket.getCreated().getTime() : System.currentTimeMillis());

                // Include data for hover/click in plugin
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("ticketId", ticketId);
                data.put("creatorName", creatorName);
                data.put("subject", ticket.getSubject() != null ? ticket.getSubject() : "");

                // Get first reply content, stripped of ** and ```
                String firstReply = "";
                if (ticket.getReplies() != null && !ticket.getReplies().isEmpty()) {
                    String content = ticket.getReplies().get(0).getContent();
                    if (content != null) {
                        firstReply = content.replace("**", "").replace("```", "");
                    }
                }
                data.put("firstReplyContent", firstReply);

                // Build ticket URL
                String domain = server.getCustomDomainOverride();
                if (domain == null || domain.isBlank()) {
                    domain = server.getCustomDomain() + ".modl.gg";
                }
                data.put("ticketUrl", "https://" + domain + "/ticket/" + ticketId);

                notif.put("data", data);
                notifications.add(notif);
            }
        } catch (Exception e) {
            // Tickets collection may not exist yet, ignore
        }

        // 2. Recent punishments issued since last sync
        try {
            Query punishmentQuery = Query.query(
                    Criteria.where("punishments").elemMatch(
                            Criteria.where("issued").gte(Date.from(lastSync))
                    )
            );
            punishmentQuery.limit(50);
            List<Player> playersWithNewPunishments = template.find(punishmentQuery, Player.class, CollectionName.PLAYERS);

            for (Player player : playersWithNewPunishments) {
                String playerName = player.getUsernames().isEmpty() ? "Unknown"
                        : player.getUsernames().get(player.getUsernames().size() - 1).username();

                for (Punishment punishment : player.getPunishments()) {
                    if (punishment.getIssued() != null && punishment.getIssued().toInstant().isAfter(lastSync)) {
                        PunishmentType punishmentType = types.stream()
                                .filter(t -> t.getOrdinal() == punishment.getTypeOrdinal())
                                .findFirst()
                                .orElse(null);
                        String typeName = punishmentType != null ? punishmentType.getName() : "Unknown";
                        String action = punishmentType != null && punishmentType.isBan() ? "banned"
                                : punishmentType != null && punishmentType.isMute() ? "muted"
                                : punishmentType != null && punishmentType.isKick() ? "kicked"
                                : "punished";

                        Map<String, Object> notif = new LinkedHashMap<>();
                        notif.put("id", "punishment_" + punishment.getId());
                        notif.put("type", "PUNISHMENT_ISSUED");
                        notif.put("message", punishment.getIssuerName() + ": " + action + " " + playerName + " (" + typeName + ")");
                        notif.put("timestamp", punishment.getIssued().getTime());
                        notifications.add(notif);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors in punishment query
        }

        // 3. Recent pardons from already-computed recentlyModifiedPunishments
        for (Map<String, Object> modified : recentlyModifiedPunishments) {
            if (!(modified.get("punishment") instanceof Map<?, ?> rawPunishment)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> punishment = (Map<String, Object>) rawPunishment;
            String username = modified.get("username") instanceof String u ? u : "Unknown";

            if (!(punishment.get("modifications") instanceof List<?> rawMods)) continue;
            for (Object rawMod : rawMods) {
                if (!(rawMod instanceof Map<?, ?> rawModMap)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> mod = (Map<String, Object>) rawModMap;
                String modType = mod.get("type") instanceof String s ? s : null;
                if ("MANUAL_PARDON".equals(modType) || "APPEAL_ACCEPT".equals(modType) || "SYSTEM_PARDON".equals(modType)) {
                    String pardoner = mod.get("issuerName") instanceof String s ? s : null;
                    String typeName = punishment.get("type") instanceof String s ? s : null;
                    Map<String, Object> notif = new LinkedHashMap<>();
                    notif.put("id", "pardon_" + punishment.get("id"));
                    notif.put("type", "PUNISHMENT_PARDONED");
                    notif.put("message", (pardoner != null ? pardoner : "System") + ": pardoned " + username + "'s " + (typeName != null ? typeName : "punishment"));
                    notif.put("timestamp", mod.get("timestamp"));
                    notifications.add(notif);
                }
            }
        }

        return notifications;
    }

    private List<Map<String, Object>> deduplicatePendingPunishments(List<Map<String, Object>> punishments) {
        // Key: "uuid|category" -> oldest punishment entry
        Map<String, Map<String, Object>> oldestByPlayerCategory = new LinkedHashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> entry : punishments) {
            String uuid = entry.get("minecraftUuid") instanceof String s ? s : "";
            if (!(entry.get("punishment") instanceof Map<?, ?> rawPunishment)) {
                result.add(entry);
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> punishment = (Map<String, Object>) rawPunishment;
            String category = punishment.get("category") instanceof String s ? s : null;

            if ("BAN".equals(category) || "MUTE".equals(category)) {
                String key = uuid + "|" + category;
                Map<String, Object> existing = oldestByPlayerCategory.get(key);
                if (existing == null) {
                    oldestByPlayerCategory.put(key, entry);
                } else {
                    if (!(existing.get("punishment") instanceof Map<?, ?> rawExisting)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existingPunishment = (Map<String, Object>) rawExisting;
                    long existingIssued = existingPunishment.get("issuedAt") instanceof Number n ? n.longValue() : 0L;
                    long currentIssued = punishment.get("issuedAt") instanceof Number n ? n.longValue() : 0L;
                    if (currentIssued < existingIssued) {
                        oldestByPlayerCategory.put(key, entry);
                    }
                }
            } else {
                result.add(entry);
            }
        }
        result.addAll(oldestByPlayerCategory.values());
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
            @Valid List<OnlinePlayer> onlinePlayers,
            ServerStatus serverStatus
    ) {}

    public record OnlinePlayer(
            @NotBlank @Pattern(regexp = RegExpConstants.UUID) String uuid,
            @NotBlank String username,
            String ipAddress
    ) {}

    public record ServerStatus(
            int onlinePlayerCount,
            int maxPlayers,
            String serverVersion,
            String timestamp
    ) {}
}
