package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.repository.MigrationMongoRepository;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffRoleMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.util.PlayerDataUtils;
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
import java.util.Optional;
import java.util.Set;

@Service
public class MinecraftSyncService {
    private final PlayerMongoRepository playerRepository;
    private final StaffMongoRepository staffRepository;
    private final StaffRoleMongoRepository staffRoleRepository;
    private final TicketMongoRepository ticketRepository;
    private final ServerMongoRepository serverRepository;
    private final MigrationMongoRepository migrationRepository;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final PunishmentLifecycleService punishmentLifecycleService;
    private final MinecraftChatLogService minecraftChatLogService;
    private final IssuerNameResolver issuerNameResolver;

    public MinecraftSyncService(
            PlayerMongoRepository playerRepository,
            StaffMongoRepository staffRepository,
            StaffRoleMongoRepository staffRoleRepository,
            TicketMongoRepository ticketRepository,
            ServerMongoRepository serverRepository,
            MigrationMongoRepository migrationRepository,
            PlayerStatusCalculator statusCalculator,
            PunishmentTypeService punishmentTypeService,
            PunishmentLifecycleService punishmentLifecycleService,
            MinecraftChatLogService minecraftChatLogService,
            IssuerNameResolver issuerNameResolver
    ) {
        this.playerRepository = playerRepository;
        this.staffRepository = staffRepository;
        this.staffRoleRepository = staffRoleRepository;
        this.ticketRepository = ticketRepository;
        this.serverRepository = serverRepository;
        this.migrationRepository = migrationRepository;
        this.statusCalculator = statusCalculator;
        this.punishmentTypeService = punishmentTypeService;
        this.punishmentLifecycleService = punishmentLifecycleService;
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
        Instant now = Instant.now();

        serverRepository.updateFirst(
                Query.query(Criteria.where("_id").is(server.getId())),
                new Update()
                        .set("lastActivityAt", Date.from(now))
                        .set("onlinePlayerCount", onlinePlayers != null ? (long) onlinePlayers.size() : 0L)
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

        markOfflinePlayers(server, onlineUuids, serverName, Date.from(now));

        if (!onlineUuids.isEmpty()) {
            List<Player> players = playerRepository.findByMinecraftUuids(server, onlineUuids);

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
                    : issuerNameResolver.batchResolve(allIssuerIds, server, staffRepository);

            for (Player player : players) {
                List<String> promoted = punishmentLifecycleService.promoteUnstartedPunishments(server, player);
                if (!promoted.isEmpty()) {
                    player = playerRepository.findByMinecraftUuid(server, player.getMinecraftUuid().toString()).orElse(null);
                    if (player == null) {
                        continue;
                    }
                }

                String uuid = player.getMinecraftUuid().toString();
                String username = PlayerDataUtils.extractLatestUsername(player.getUsernames());

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
        List<Map<String, Object>> staffNotifications = getRecentStaffEvents(server, lastSync, types, recentlyModifiedPunishments);

        Map<String, String> onlinePlayerIps = new HashMap<>();
        if (onlinePlayers != null) {
            for (OnlinePlayerInput onlinePlayer : onlinePlayers) {
                if (onlinePlayer.uuid() != null && onlinePlayer.ipAddress() != null) {
                    onlinePlayerIps.put(onlinePlayer.uuid(), onlinePlayer.ipAddress());
                }
            }
        }

        List<Map<String, Object>> activeStaffMembers = getActiveStaffMembers(server, onlinePlayerIps);

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
            List<Staff> pendingStaff = staffRepository.findWithPendingTwoFactorDelivery(server);
            if (!pendingStaff.isEmpty()) {
                data.put("staff2faVerifications", pendingStaff.stream()
                        .map(staff -> Map.<String, Object>of("minecraftUuid", staff.getAssignedMinecraftUuid()))
                        .toList());
                staffRepository.clearPendingTwoFactorDelivery(server);
            }
        } catch (Exception ignored) {
        }

        try {
            Optional<MigrationStatus> activeMigration = migrationRepository.findActiveMigration(server);
            activeMigration.ifPresent(migration -> data.put("migrationTask", Map.of(
                    "taskId", migration.getTaskId(),
                    "type", migration.getType()
            )));
        } catch (Exception ignored) {
        }

        return Map.of(
                "timestamp", now.toString(),
                "data", data
        );
    }

    private void markOfflinePlayers(Server server, Set<String> onlineUuids, String serverName, Date logoutTime) {
        Criteria staleOnlineCriteria = MongoQueries.where(PlayerFields.DATA_IS_ONLINE).is(true)
                .and(PlayerFields.MINECRAFT_UUID).nin(onlineUuids);
        if (serverName != null && !serverName.isBlank()) {
            staleOnlineCriteria = staleOnlineCriteria.and(PlayerFields.DATA_LAST_SERVER).is(serverName);
        }
        playerRepository.markStalePlayersOffline(server, staleOnlineCriteria, logoutTime);
    }

    private List<Map<String, Object>> getRecentStaffEvents(
            Server server,
            Instant lastSync,
            List<PunishmentType> types,
            List<Map<String, Object>> recentlyModifiedPunishments
    ) {
        List<Map<String, Object>> notifications = new ArrayList<>();

        try {
            List<Ticket> recentTickets = ticketRepository.findCreatedAfterExcludingUnfinished(server, Date.from(lastSync), 20);

            for (Ticket ticket : recentTickets) {
                TicketCategory ticketType = ticket.getType();
                if (ticketType == TicketCategory.APPLICATION) {
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
                if (ticketType != null && ticketType.isReport()) {
                    String reportedPlayer = ticket.getReportedPlayer() != null ? ticket.getReportedPlayer() : "Unknown";
                    String categoryLabel = ticketType == TicketCategory.CHAT ? "Chat" : "Gameplay";
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

                Map<String, Object> ticketData = new LinkedHashMap<>();
                ticketData.put("ticketId", ticket.getId());
                ticketData.put("creatorName", creatorName);
                ticketData.put("subject", ticket.getSubject() != null ? ticket.getSubject() : "");

                String firstReply = "";
                if (ticket.getReplies() != null && !ticket.getReplies().isEmpty()) {
                    String content = ticket.getReplies().get(0).getContent();
                    if (content != null) {
                        firstReply = content.replace("**", "").replace("```", "");
                    }
                }
                ticketData.put("firstReplyContent", firstReply);

                String domain = server.getCustomDomainOverride();
                if (domain == null || domain.isBlank()) {
                    domain = server.getCustomDomain() + ".modl.gg";
                }
                ticketData.put("ticketUrl", "https://" + domain + "/ticket/" + ticket.getId());
                ticketData.put("ticketType", ticketType != null ? ticketType.getId() : "");
                if (ticketType != null && ticketType.isReport()) {
                    ticketData.put("reportedPlayer", ticket.getReportedPlayer() != null ? ticket.getReportedPlayer() : "");
                    ticketData.put("category", ticketType.getId());
                }

                notification.put("data", ticketData);
                notifications.add(notification);
            }
        } catch (Exception ignored) {
        }

        try {
            List<Player> playersWithNewPunishments = playerRepository.findWithPunishmentsIssuedAfter(server, Date.from(lastSync), 50);

            Set<String> issuerIds = new HashSet<>();
            for (Player player : playersWithNewPunishments) {
                for (Punishment punishment : player.getPunishments()) {
                    if (punishment.getIssuerId() != null) issuerIds.add(punishment.getIssuerId());
                }
            }
            Map<String, String> resolvedIssuers = issuerIds.isEmpty()
                    ? Map.of()
                    : issuerNameResolver.batchResolve(issuerIds, server, staffRepository);

            for (Player player : playersWithNewPunishments) {
                String playerName = PlayerDataUtils.extractLatestUsername(player.getUsernames());

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

    private List<Map<String, Object>> getActiveStaffMembers(Server server, Map<String, String> onlinePlayerIps) {
        List<Staff> staffWithMinecraft = staffRepository.findAssignedMinecraftStaff(server);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Staff staff : staffWithMinecraft) {
            StaffRole role = staffRoleRepository.findByName(server, staff.getRole()).orElse(null);
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
