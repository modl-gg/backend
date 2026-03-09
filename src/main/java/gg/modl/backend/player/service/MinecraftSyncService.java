package gg.modl.backend.player.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketBucket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MinecraftSyncService {
    private final TenantMongoAccess tenantMongoAccess;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final PunishmentService punishmentService;
    private final MinecraftChatLogService minecraftChatLogService;
    private final IssuerNameResolver issuerNameResolver;

    public MinecraftSyncService(
            TenantMongoAccess tenantMongoAccess,
            PlayerStatusCalculator statusCalculator,
            PunishmentTypeService punishmentTypeService,
            PunishmentService punishmentService,
            MinecraftChatLogService minecraftChatLogService,
            IssuerNameResolver issuerNameResolver
    ) {
        this.tenantMongoAccess = tenantMongoAccess;
        this.statusCalculator = statusCalculator;
        this.punishmentTypeService = punishmentTypeService;
        this.punishmentService = punishmentService;
        this.minecraftChatLogService = minecraftChatLogService;
        this.issuerNameResolver = issuerNameResolver;
    }

    public Map<String, Object> sync(
            Server server,
            String lastSyncTimestamp,
            List<OnlinePlayerInput> onlinePlayers,
            String serverName,
            List<ChatLogInput> chatLogs,
            List<CommandLogInput> commandLogs
    ) {
        MongoTemplate template = tenantMongoAccess.forServer(server);
        Instant now = Instant.now();

        tenantMongoAccess.global().updateFirst(
                Query.query(Criteria.where("_id").is(server.getId())),
                new Update()
                        .set("lastActivityAt", Date.from(now))
                        .set("onlinePlayerCount", onlinePlayers != null ? (long) onlinePlayers.size() : 0L),
                Server.class
        );
        Instant lastSync = lastSyncTimestamp != null
                ? Instant.parse(lastSyncTimestamp)
                : now.minusSeconds(30);

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        List<Map<String, Object>> pendingPunishments = new ArrayList<>();
        List<Map<String, Object>> recentlyModifiedPunishments = new ArrayList<>();
        List<Map<String, Object>> playerNotifications = new ArrayList<>();

        Set<String> onlineUuids = new HashSet<>();
        if (onlinePlayers != null) {
            for (OnlinePlayerInput onlinePlayer : onlinePlayers) {
                if (onlinePlayer.uuid() != null) {
                    onlineUuids.add(onlinePlayer.uuid());
                }
            }
        }

        Criteria staleOnlineCriteria = MongoQueries.where(PlayerFields.DATA_IS_ONLINE).is(true)
                .and(PlayerFields.MINECRAFT_UUID).nin(onlineUuids);
        if (serverName != null && !serverName.isBlank()) {
            staleOnlineCriteria = staleOnlineCriteria.and(PlayerFields.DATA_LAST_SERVER).is(serverName);
        }

        Query staleOnlineQuery = Query.query(staleOnlineCriteria);
        Update markOffline = new Update()
                .set(PlayerFields.DATA_IS_ONLINE, false)
                .set(PlayerFields.DATA_LAST_LOGOUT, Date.from(now));
        template.updateMulti(staleOnlineQuery, markOffline, Player.class, CollectionName.PLAYERS);

        if (!onlineUuids.isEmpty()) {
            Query playerQuery = Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).in(onlineUuids));
            List<Player> players = template.find(playerQuery, Player.class, CollectionName.PLAYERS);

            // Batch resolve issuer IDs for all punishments across all players
            Set<String> allIssuerIds = new HashSet<>();
            for (Player p : players) {
                for (Punishment pun : p.getPunishments()) {
                    if (pun.getIssuerId() != null) allIssuerIds.add(pun.getIssuerId());
                    for (var m : pun.getModifications()) {
                        if (m.issuerId() != null) allIssuerIds.add(m.issuerId());
                    }
                }
            }
            Map<String, String> resolvedIssuers = allIssuerIds.isEmpty()
                    ? Map.of()
                    : issuerNameResolver.batchResolve(allIssuerIds, template);

            for (Player player : players) {
                List<String> promoted = punishmentService.promoteUnstartedPunishments(server, player);
                if (!promoted.isEmpty()) {
                    Query refetchQuery = Query.query(MongoQueries.where(PlayerFields.MINECRAFT_UUID).is(player.getMinecraftUuid().toString()));
                    player = template.findOne(refetchQuery, Player.class, CollectionName.PLAYERS);
                    if (player == null) {
                        continue;
                    }
                }

                String uuid = player.getMinecraftUuid().toString();
                String username = player.getUsernames().isEmpty()
                        ? "Unknown"
                        : player.getUsernames().get(player.getUsernames().size() - 1).username();

                Set<String> categoriesWithActiveStarted = new HashSet<>();
                Map<String, Punishment> oldestUnstartedPerCategory = new LinkedHashMap<>();

                for (Punishment punishment : player.getPunishments()) {
                    boolean active = statusCalculator.isPunishmentActive(punishment);
                    String category = statusCalculator.getEffectiveCategory(punishment, types);
                    if (!active) {
                        continue;
                    }

                    if (category != null && punishment.getStarted() != null) {
                        categoriesWithActiveStarted.add(category);
                    } else if (category != null && punishment.getStarted() == null) {
                        Punishment existing = oldestUnstartedPerCategory.get(category);
                        if (existing == null || punishment.getIssued().before(existing.getIssued())) {
                            oldestUnstartedPerCategory.put(category, punishment);
                        }
                    }
                }

                for (Punishment punishment : player.getPunishments()) {
                    boolean active = statusCalculator.isPunishmentActive(punishment);
                    boolean recentlyModified = punishment.getModifications().stream()
                            .anyMatch(modification -> modification.date() != null && modification.date().toInstant().isAfter(lastSync));

                    if (recentlyModified) {
                        recentlyModifiedPunishments.add(Map.of(
                                "minecraftUuid", uuid,
                                "username", username,
                                "punishment", PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)
                        ));
                    }

                    if (!active || punishment.getStarted() != null) {
                        continue;
                    }

                    String category = statusCalculator.getEffectiveCategory(punishment, types);
                    if (category != null) {
                        if (categoriesWithActiveStarted.contains(category)) {
                            continue;
                        }
                        if (oldestUnstartedPerCategory.get(category) != punishment) {
                            continue;
                        }
                    }

                    pendingPunishments.add(Map.of(
                            "minecraftUuid", uuid,
                            "username", username,
                            "punishment", PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)
                    ));
                }

                for (Punishment punishment : player.getPunishments()) {
                    if (!statusCalculator.isPunishmentActive(punishment)
                            || punishment.getStarted() == null
                            || punishment.getIssued() == null
                            || !punishment.getIssued().toInstant().isAfter(lastSync)) {
                        continue;
                    }

                    String category = statusCalculator.getEffectiveCategory(punishment, types);
                    if (category == null) {
                        continue;
                    }

                    pendingPunishments.add(Map.of(
                            "minecraftUuid", uuid,
                            "username", username,
                            "punishment", PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)
                    ));
                }

                for (Punishment punishment : player.getPunishments()) {
                    if (punishment.getTypeOrdinal() != 0 || punishment.getStarted() != null) {
                        continue;
                    }

                    pendingPunishments.add(Map.of(
                            "minecraftUuid", uuid,
                            "username", username,
                            "punishment", PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)
                    ));
                }

                Set<String> pardonedCategories = new HashSet<>();
                for (Punishment punishment : player.getPunishments()) {
                    boolean recentlyPardoned = punishment.getModifications().stream()
                            .anyMatch(modification -> modification.date() != null
                                    && modification.date().toInstant().isAfter(lastSync)
                                    && ("MANUAL_PARDON".equals(modification.type())
                                    || "APPEAL_ACCEPT".equals(modification.type())
                                    || "SYSTEM_PARDON".equals(modification.type())));
                    if (recentlyPardoned) {
                        String category = statusCalculator.getEffectiveCategory(punishment, types);
                        if (category != null) {
                            pardonedCategories.add(category);
                        }
                    }
                }

                if (!pardonedCategories.isEmpty()) {
                    for (Punishment punishment : player.getPunishments()) {
                        if (!statusCalculator.isPunishmentActive(punishment) || punishment.getStarted() == null) {
                            continue;
                        }

                        String category = statusCalculator.getEffectiveCategory(punishment, types);
                        if (category != null && pardonedCategories.contains(category)) {
                            pendingPunishments.add(Map.of(
                                    "minecraftUuid", uuid,
                                    "username", username,
                                    "punishment", PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)
                            ));
                            pardonedCategories.remove(category);
                        }
                    }
                }

                Object rawPending = player.getData().get("pendingNotifications");
                if (rawPending instanceof List<?> pendingList) {
                    for (Object item : pendingList) {
                        if (!(item instanceof Map<?, ?> notification)) {
                            continue;
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typedNotification = (Map<String, Object>) notification;
                        Map<String, Object> payload = new HashMap<>(typedNotification);
                        payload.put("targetPlayerUuid", uuid);
                        playerNotifications.add(payload);
                    }
                }
            }
        }

        pendingPunishments = deduplicatePendingPunishments(pendingPunishments);
        List<Map<String, Object>> staffNotifications = getRecentStaffEvents(template, lastSync, types, recentlyModifiedPunishments, server);

        Map<String, String> onlinePlayerIps = new HashMap<>();
        if (onlinePlayers != null) {
            for (OnlinePlayerInput onlinePlayer : onlinePlayers) {
                if (onlinePlayer.uuid() != null && onlinePlayer.ipAddress() != null) {
                    onlinePlayerIps.put(onlinePlayer.uuid(), onlinePlayer.ipAddress());
                }
            }
        }

        List<Map<String, Object>> activeStaffMembers = getActiveStaffMembers(template, onlinePlayerIps);

        if (chatLogs != null && !chatLogs.isEmpty()) {
            minecraftChatLogService.submitChatLogs(server, chatLogs.stream()
                    .map(entry -> new MinecraftChatLogService.ChatLogCommand(
                            entry.uuid(),
                            entry.username(),
                            entry.message(),
                            entry.timestamp(),
                            entry.server()
                    ))
                    .toList());
        }
        if (commandLogs != null && !commandLogs.isEmpty()) {
            minecraftChatLogService.submitCommandLogs(server, commandLogs.stream()
                    .map(entry -> new MinecraftChatLogService.CommandLogCommand(
                            entry.uuid(),
                            entry.username(),
                            entry.command(),
                            entry.timestamp(),
                            entry.server()
                    ))
                    .toList());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pendingPunishments", pendingPunishments);
        data.put("recentlyStartedPunishments", List.of());
        data.put("recentlyModifiedPunishments", recentlyModifiedPunishments);
        data.put("playerNotifications", playerNotifications);
        data.put("staffNotifications", staffNotifications);
        data.put("activeStaffMembers", activeStaffMembers);
        data.put("pendingStatWipes", List.of());
        data.put("staffPermissionsUpdatedAt", server.getStaffPermissionsUpdatedAt() != null
                ? server.getStaffPermissionsUpdatedAt().getTime()
                : null);
        data.put("punishmentTypesUpdatedAt", server.getPunishmentTypesUpdatedAt() != null
                ? server.getPunishmentTypesUpdatedAt().getTime()
                : null);

        try {
            Query pendingQuery = Query.query(Criteria.where("twoFactorPendingDelivery").is(true)
                    .and("assignedMinecraftUuid").exists(true).ne(null).ne(""));
            List<Staff> pendingStaff = template.find(pendingQuery, Staff.class, CollectionName.STAFF);
            if (!pendingStaff.isEmpty()) {
                data.put("staff2faVerifications", pendingStaff.stream()
                        .map(staff -> Map.<String, Object>of("minecraftUuid", staff.getAssignedMinecraftUuid()))
                        .toList());
                template.updateMulti(pendingQuery, new Update().set("twoFactorPendingDelivery", false), Staff.class, CollectionName.STAFF);
            }
        } catch (Exception ignored) {
        }

        try {
            Query migrationQuery = Query.query(Criteria.where("status").is("building_json"));
            MigrationStatus activeMigration = template.findOne(migrationQuery, MigrationStatus.class, "migrations");
            if (activeMigration != null) {
                data.put("migrationTask", Map.of(
                        "taskId", activeMigration.getTaskId(),
                        "type", activeMigration.getType()
                ));
            }
        } catch (Exception ignored) {
        }

        return Map.of(
                "timestamp", now.toString(),
                "data", data
        );
    }

    private List<Map<String, Object>> getRecentStaffEvents(
            MongoTemplate template,
            Instant lastSync,
            List<PunishmentType> types,
            List<Map<String, Object>> recentlyModifiedPunishments,
            Server server
    ) {
        List<Map<String, Object>> notifications = new ArrayList<>();

        try {
            Query ticketQuery = Query.query(Criteria.where("created").gte(Date.from(lastSync))
                    .and("status").ne(TicketStatus.UNFINISHED.getId()));
            ticketQuery.limit(20);
            List<Ticket> recentTickets = template.find(ticketQuery, Ticket.class, CollectionName.TICKETS);

            for (Ticket ticket : recentTickets) {
                TicketBucket ticketType = ticket.getType();
                if (ticketType == TicketBucket.STAFF) {
                    continue;
                }

                String creatorName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Unknown";
                String createdServer = null;
                if (ticket.getData() != null) {
                    Object value = ticket.getData().get("createdServer");
                    if (value instanceof String serverValue && !serverValue.isBlank()) {
                        createdServer = serverValue;
                    }
                }

                String message;
                if (ticketType == TicketBucket.REPORT) {
                    String reportedPlayer = ticket.getReportedPlayer() != null ? ticket.getReportedPlayer() : "Unknown";
                    TicketCategory category = ticket.getCategory();
                    String categoryLabel = category == TicketCategory.CHAT ? "Chat" : "Gameplay";
                    message = creatorName + ": reported " + reportedPlayer;
                    if (createdServer != null) {
                        message += " on " + createdServer;
                    }
                    message += " (" + categoryLabel + ")";
                } else {
                    message = creatorName + ": created " + ticket.getId();
                    if (createdServer != null) {
                        message += " on " + createdServer;
                    }
                }

                Map<String, Object> notification = new LinkedHashMap<>();
                notification.put("id", "ticket_" + ticket.getId());
                notification.put("type", "TICKET_CREATED");
                notification.put("message", message);
                notification.put("timestamp", ticket.getCreated() != null ? ticket.getCreated().getTime() : System.currentTimeMillis());

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("ticketId", ticket.getId());
                data.put("creatorName", creatorName);
                data.put("subject", ticket.getSubject() != null ? ticket.getSubject() : "");

                String firstReply = "";
                if (ticket.getReplies() != null && !ticket.getReplies().isEmpty()) {
                    String content = ticket.getReplies().get(0).getContent();
                    if (content != null) {
                        firstReply = content.replace("**", "").replace("```", "");
                    }
                }
                data.put("firstReplyContent", firstReply);

                String domain = server.getCustomDomainOverride();
                if (domain == null || domain.isBlank()) {
                    domain = server.getCustomDomain() + ".modl.gg";
                }
                data.put("ticketUrl", "https://" + domain + "/ticket/" + ticket.getId());
                data.put("ticketType", ticketType != null ? ticketType.getId() : "");
                if (ticketType == TicketBucket.REPORT) {
                    data.put("reportedPlayer", ticket.getReportedPlayer() != null ? ticket.getReportedPlayer() : "");
                    data.put("category", ticket.getCategory() != null ? ticket.getCategory().getId() : "");
                }

                notification.put("data", data);
                notifications.add(notification);
            }
        } catch (Exception ignored) {
        }

        try {
            Query punishmentQuery = Query.query(Criteria.where("punishments").elemMatch(
                    Criteria.where("issued").gte(Date.from(lastSync))
            ));
            punishmentQuery.limit(50);
            List<Player> playersWithNewPunishments = template.find(punishmentQuery, Player.class, CollectionName.PLAYERS);

            Set<String> issuerIds = new HashSet<>();
            for (Player player : playersWithNewPunishments) {
                for (Punishment punishment : player.getPunishments()) {
                    if (punishment.getIssuerId() != null) issuerIds.add(punishment.getIssuerId());
                }
            }
            Map<String, String> resolvedIssuers = issuerIds.isEmpty()
                    ? Map.of()
                    : issuerNameResolver.batchResolve(issuerIds, template);

            for (Player player : playersWithNewPunishments) {
                String playerName = player.getUsernames().isEmpty()
                        ? "Unknown"
                        : player.getUsernames().get(player.getUsernames().size() - 1).username();

                for (Punishment punishment : player.getPunishments()) {
                    if (punishment.getIssued() != null && punishment.getIssued().toInstant().isAfter(lastSync)) {
                        PunishmentType punishmentType = types.stream()
                                .filter(type -> type.getOrdinal() == punishment.getTypeOrdinal())
                                .findFirst()
                                .orElse(null);
                        String typeName = punishmentType != null ? punishmentType.getName() : "Unknown";
                        String action = punishmentType != null && punishmentType.isBan() ? "banned"
                                : punishmentType != null && punishmentType.isMute() ? "muted"
                                : punishmentType != null && punishmentType.isKick() ? "kicked"
                                : "punished";

                        String issuerName = issuerNameResolver.resolve(punishment.getIssuerId(), punishment.getIssuerName(), resolvedIssuers);
                        notifications.add(Map.of(
                                "id", "punishment_" + punishment.getId(),
                                "type", "PUNISHMENT_ISSUED",
                                "message", issuerName + ": " + action + " " + playerName + " (" + typeName + ")",
                                "timestamp", punishment.getIssued().getTime()
                        ));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        for (Map<String, Object> modified : recentlyModifiedPunishments) {
            if (!(modified.get("punishment") instanceof Map<?, ?> rawPunishment)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> punishment = (Map<String, Object>) rawPunishment;
            String username = modified.get("username") instanceof String value ? value : "Unknown";

            if (!(punishment.get("modifications") instanceof List<?> rawModifications)) {
                continue;
            }

            for (Object rawModification : rawModifications) {
                if (!(rawModification instanceof Map<?, ?> rawModificationMap)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> modification = (Map<String, Object>) rawModificationMap;
                String type = modification.get("type") instanceof String value ? value : null;
                if ("MANUAL_PARDON".equals(type) || "APPEAL_ACCEPT".equals(type) || "SYSTEM_PARDON".equals(type)) {
                    String pardoner = modification.get("issuerName") instanceof String value ? value : "System";
                    String punishmentType = punishment.get("type") instanceof String value ? value : "punishment";
                    notifications.add(Map.of(
                            "id", "pardon_" + punishment.get("id"),
                            "type", "PUNISHMENT_PARDONED",
                            "message", pardoner + ": pardoned " + username + "'s " + punishmentType,
                            "timestamp", modification.get("timestamp")
                    ));
                }
            }
        }

        return notifications;
    }

    private List<Map<String, Object>> deduplicatePendingPunishments(List<Map<String, Object>> punishments) {
        Map<String, Map<String, Object>> oldestByPlayerCategory = new LinkedHashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> entry : punishments) {
            String uuid = entry.get("minecraftUuid") instanceof String value ? value : "";
            if (!(entry.get("punishment") instanceof Map<?, ?> rawPunishment)) {
                result.add(entry);
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> punishment = (Map<String, Object>) rawPunishment;
            String category = punishment.get("category") instanceof String value ? value : null;

            if ("BAN".equals(category) || "MUTE".equals(category)) {
                String key = uuid + "|" + category;
                Map<String, Object> existing = oldestByPlayerCategory.get(key);
                if (existing == null) {
                    oldestByPlayerCategory.put(key, entry);
                } else if (existing.get("punishment") instanceof Map<?, ?> rawExisting) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existingPunishment = (Map<String, Object>) rawExisting;
                    long existingIssued = existingPunishment.get("issuedAt") instanceof Number number ? number.longValue() : 0L;
                    long currentIssued = punishment.get("issuedAt") instanceof Number number ? number.longValue() : 0L;
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

    private List<Map<String, Object>> getActiveStaffMembers(MongoTemplate template, Map<String, String> onlinePlayerIps) {
        Query staffQuery = Query.query(Criteria.where("assignedMinecraftUuid").exists(true).ne(null).ne(""));
        List<Staff> staffWithMinecraft = template.find(staffQuery, Staff.class, CollectionName.STAFF);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Staff staff : staffWithMinecraft) {
            Query roleQuery = Query.query(Criteria.where("name").is(staff.getRole()));
            StaffRole role = template.findOne(roleQuery, StaffRole.class, CollectionName.STAFF_ROLES);
            List<String> permissions = role != null ? role.getPermissions() : List.of();

            String currentIp = onlinePlayerIps.get(staff.getAssignedMinecraftUuid());
            boolean sessionValid = staff.getTwoFactorSessionExpiresAt() != null
                    && staff.getTwoFactorSessionExpiresAt() > Instant.now().toEpochMilli()
                    && staff.getTwoFactorSessionIp() != null
                    && staff.getTwoFactorSessionIp().equals(currentIp);

            Map<String, Object> entry = new HashMap<>();
            entry.put("minecraftUuid", staff.getAssignedMinecraftUuid());
            entry.put("minecraftUsername", staff.getAssignedMinecraftUsername() != null ? staff.getAssignedMinecraftUsername() : "");
            entry.put("staffUsername", staff.getUsername() != null ? staff.getUsername() : "");
            entry.put("staffId", staff.getId());
            entry.put("staffRole", staff.getRole() != null ? staff.getRole() : "");
            entry.put("permissions", permissions);
            entry.put("email", staff.getEmail() != null ? staff.getEmail() : "");
            entry.put("twoFactorSessionValid", sessionValid);
            result.add(entry);
        }

        return result;
    }

    public record OnlinePlayerInput(String uuid, String username, String ipAddress) {
    }

    public record ChatLogInput(String uuid, String username, String message, long timestamp, String server) {
    }

    public record CommandLogInput(String uuid, String username, String command, long timestamp, String server) {
    }
}
