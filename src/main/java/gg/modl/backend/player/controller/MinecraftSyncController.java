package gg.modl.backend.player.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.player.service.PunishmentService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.ticket.data.Ticket;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class MinecraftSyncController {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final PunishmentService punishmentService;

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
                        Map<String, Object> simplePunishment = toSimplePunishment(punishment, types);
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

                    Map<String, Object> simplePunishment = toSimplePunishment(punishment, types);
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
                    if (category == null) continue; // Not a ban or mute — no enforcement needed

                    Map<String, Object> simplePunishment = toSimplePunishment(punishment, types);
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
                            Map<String, Object> simplePunishment = toSimplePunishment(punishment, types);
                            pendingPunishments.add(Map.of(
                                    "minecraftUuid", uuid,
                                    "username", username,
                                    "punishment", simplePunishment
                            ));
                            pardonedCategories.remove(cat); // Only re-send one per category
                        }
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

    private Map<String, Object> toSimplePunishment(Punishment punishment, List<PunishmentType> types) {
        Map<String, Object> data = punishment.getData();
        Date expires = statusCalculator.getEffectiveExpiry(punishment);

        PunishmentType punishmentType = types.stream()
                .filter(t -> t.getOrdinal() == punishment.getType_ordinal())
                .findFirst()
                .orElse(null);

        String typeName = punishmentType != null ? punishmentType.getName() : "Unknown";
        String effectiveCategory = statusCalculator.getEffectiveCategory(punishmentType, data);
        String category = effectiveCategory != null ? effectiveCategory : "OTHER";
        String playerDescription = punishmentType != null ? punishmentType.getPlayerDescription() : null;

        // For manual punishments (ordinals 0-5: kick, mute, ban, security ban, linked ban, blacklist),
        // the reason is stored as the first non-auto-generated note
        String reason = null;
        if (punishment.getType_ordinal() <= 5 && punishment.getNotes() != null && !punishment.getNotes().isEmpty()) {
            for (var note : punishment.getNotes()) {
                String noteText = note.text();
                if (noteText != null && !isAutoGeneratedNote(noteText)) {
                    reason = noteText;
                    break;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", punishment.getId());
        result.put("type", typeName);
        result.put("category", category);
        result.put("ordinal", punishment.getType_ordinal());
        result.put("started", punishment.getStarted() != null);
        result.put("expiration", expires != null ? expires.getTime() : null);
        result.put("description", reason != null ? reason : "No reason specified");
        result.put("issuerName", punishment.getIssuerName());
        result.put("issuedAt", punishment.getIssued().getTime());
        result.put("playerDescription", playerDescription);
        result.put("modifications", punishment.getModifications().stream().map(m -> {
                Map<String, Object> modMap = new LinkedHashMap<>();
                modMap.put("type", m.type());
                modMap.put("timestamp", m.date() != null ? m.date().getTime() : null);
                modMap.put("effectiveDuration", m.effectiveDuration() != null ? m.effectiveDuration() : 0L);
                modMap.put("issuerName", m.issuerName());
                return modMap;
        }).toList());

        return result;
    }

    private boolean isAutoGeneratedNote(String noteText) {
        if (noteText == null) return true;
        String lower = noteText.toLowerCase();
        return lower.equals("issued punishment") ||
               lower.equals("pardoned punishment") ||
               lower.equals("added evidence") ||
               lower.startsWith("changed duration to ") ||
               lower.startsWith("enabled ") ||
               lower.startsWith("disabled ");
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
                String creatorName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Unknown";
                String ticketId = ticket.getId();
                String serverName = server.getServerName() != null ? server.getServerName() : "Unknown";

                Map<String, Object> notif = new LinkedHashMap<>();
                notif.put("id", "ticket_" + ticketId);
                notif.put("type", "TICKET_CREATED");
                notif.put("message", creatorName + ": created " + ticketId + " on " + serverName);
                notif.put("timestamp", ticket.getCreated() != null ? ticket.getCreated().getTime() : System.currentTimeMillis());

                // Include data for hover/click in plugin
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("ticketId", ticketId);
                data.put("creatorName", creatorName);
                data.put("serverName", serverName);
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
                                .filter(t -> t.getOrdinal() == punishment.getType_ordinal())
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
            @SuppressWarnings("unchecked")
            Map<String, Object> punishment = (Map<String, Object>) modified.get("punishment");
            String username = (String) modified.get("username");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> modifications = (List<Map<String, Object>>) punishment.get("modifications");
            if (modifications != null) {
                for (Map<String, Object> mod : modifications) {
                    String modType = (String) mod.get("type");
                    if ("MANUAL_PARDON".equals(modType) || "APPEAL_ACCEPT".equals(modType) || "SYSTEM_PARDON".equals(modType)) {
                        String pardoner = (String) mod.get("issuerName");
                        String typeName = (String) punishment.get("type");
                        Map<String, Object> notif = new LinkedHashMap<>();
                        notif.put("id", "pardon_" + punishment.get("id"));
                        notif.put("type", "PUNISHMENT_PARDONED");
                        notif.put("message", (pardoner != null ? pardoner : "System") + ": pardoned " + username + "'s " + (typeName != null ? typeName : "punishment"));
                        notif.put("timestamp", mod.get("timestamp"));
                        notifications.add(notif);
                    }
                }
            }
        }

        return notifications;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> deduplicatePendingPunishments(List<Map<String, Object>> punishments) {
        // Key: "uuid|category" -> oldest punishment entry
        Map<String, Map<String, Object>> oldestByPlayerCategory = new LinkedHashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> entry : punishments) {
            String uuid = (String) entry.get("minecraftUuid");
            Map<String, Object> punishment = (Map<String, Object>) entry.get("punishment");
            String category = (String) punishment.get("category");

            if ("BAN".equals(category) || "MUTE".equals(category)) {
                String key = uuid + "|" + category;
                Map<String, Object> existing = oldestByPlayerCategory.get(key);
                if (existing == null) {
                    oldestByPlayerCategory.put(key, entry);
                } else {
                    Map<String, Object> existingPunishment = (Map<String, Object>) existing.get("punishment");
                    long existingIssued = (Long) existingPunishment.get("issuedAt");
                    long currentIssued = (Long) punishment.get("issuedAt");
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
