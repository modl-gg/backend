package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.MigrationMongoRepository;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerInstanceSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentModificationType;
import gg.modl.backend.player.data.punishment.EnforcementCategory;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import java.net.URI;
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
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinecraftSyncService {
    private final PlayerMongoRepository playerRepository;
    private final StaffMongoRepository staffRepository;
    private final ServerMongoRepository serverRepository;
    private final MigrationMongoRepository migrationRepository;
    private final ServerInstanceSnapshotMongoRepository serverInstanceSnapshotRepository;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final PunishmentLifecycleService punishmentLifecycleService;
    private final MinecraftChatLogService minecraftChatLogService;
    private final IssuerNameResolver issuerNameResolver;
    private final SyncStaffEventService syncStaffEventService;
    private final SyncActiveStaffService syncActiveStaffService;

    public Map<String, Object> sync(
        Server server,
        String lastSyncTimestamp,
        List<OnlinePlayerInput> onlinePlayers,
        String serverName,
        List<ChatLogInput> chatLogs,
        List<CommandLogInput> commandLogs,
        ServerStatusInput serverStatus,
        String clientIp
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
                    onlineUuids.add(normalizeUuid(onlinePlayer.uuid()));
                }
            }
        }

        markOfflinePlayers(server, onlineUuids, serverName, Date.from(now));

        if (!onlineUuids.isEmpty()) {
            List<Player> players = playerRepository.findByMinecraftUuids(server, onlineUuids);

            Set<String> allIssuerIds = new HashSet<>();
            for (Player p : players) {
                for (Punishment pun : p.getPunishments()) {
                    if (pun.getIssuerId() != null) {
                        allIssuerIds.add(pun.getIssuerId());
                    }
                    for (PunishmentModification m : pun.getModifications()) {
                        if (m.issuerId() != null) {
                            allIssuerIds.add(m.issuerId());
                        }
                    }
                }
            }
            Map<String, String> resolvedIssuers = allIssuerIds.isEmpty()
                                                  ? Map.of()
                                                  : issuerNameResolver.batchResolve(allIssuerIds, server);

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

                // Pass 1: classify all punishments in a single iteration
                Set<String> categoriesWithActiveStarted = new HashSet<>();
                Map<String, Punishment> oldestUnstartedPerCategory = new LinkedHashMap<>();
                Set<String> pardonedCategories = new HashSet<>();

                for (Punishment punishment : player.getPunishments()) {
                    boolean active = statusCalculator.isPunishmentActive(punishment);
                    String category = statusCalculator.getEffectiveCategory(punishment, types);

                    boolean recentlyModified = punishment.getModifications()
                        .stream()
                        .anyMatch(mod -> mod.date() != null && mod.date().toInstant().isAfter(lastSync));
                    if (recentlyModified) {
                        recentlyModifiedPunishments.add(Map.of(
                            "minecraftUuid", uuid,
                            "username", username,
                            "punishment", PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)
                        ));
                    }

                    boolean recentlyPardoned = punishment.getModifications()
                        .stream()
                        .anyMatch(mod -> mod.date() != null
                                         && mod.date().toInstant().isAfter(lastSync)
                                         && PunishmentModificationType.isPardon(mod.type()));
                    if (recentlyPardoned && category != null) {
                        pardonedCategories.add(category);
                    }

                    // Unstarted kicks (ordinal 0)
                    if (punishment.getTypeOrdinal() == 0 && punishment.getStarted() == null) {
                        pendingPunishments.add(Map.of(
                            "minecraftUuid", uuid,
                            "username", username,
                            "punishment", PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)
                        ));
                        continue;
                    }

                    if (!active) {
                        continue;
                    }

                    // Newly issued active punishments since last sync
                    if (punishment.getStarted() != null
                        && punishment.getIssued() != null
                        && punishment.getIssued().toInstant().isAfter(lastSync)
                        && category != null) {
                        pendingPunishments.add(Map.of(
                            "minecraftUuid", uuid,
                            "username", username,
                            "punishment", PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)
                        ));
                    }

                    // Track active started vs unstarted per category
                    if (category != null && punishment.getStarted() != null) {
                        categoriesWithActiveStarted.add(category);
                    } else if (category != null && punishment.getStarted() == null) {
                        Punishment existing = oldestUnstartedPerCategory.get(category);
                        if (existing == null || punishment.getIssued().before(existing.getIssued())) {
                            oldestUnstartedPerCategory.put(category, punishment);
                        }
                    }
                }

                // Add oldest unstarted punishments that don't have an active started one in the same category
                for (Map.Entry<String, Punishment> entry : oldestUnstartedPerCategory.entrySet()) {
                    if (!categoriesWithActiveStarted.contains(entry.getKey())) {
                        pendingPunishments.add(Map.of(
                            "minecraftUuid", uuid,
                            "username", username,
                            "punishment", PunishmentMapper.toSimplePunishment(entry.getValue(), types, statusCalculator, resolvedIssuers)
                        ));
                    }
                }

                // Pass 2: find active replacements for recently pardoned categories
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
        List<Map<String, Object>> staffNotifications = syncStaffEventService.collectStaffEvents(server, lastSync, types, recentlyModifiedPunishments);

        Map<String, String> onlinePlayerIps = new HashMap<>();
        if (onlinePlayers != null) {
            for (OnlinePlayerInput onlinePlayer : onlinePlayers) {
                if (onlinePlayer.uuid() != null && onlinePlayer.ipAddress() != null) {
                    onlinePlayerIps.put(normalizeUuid(onlinePlayer.uuid()), onlinePlayer.ipAddress());
                }
            }
        }

        List<Map<String, Object>> activeStaffMembers = syncActiveStaffService.getActiveStaffMembers(server, onlinePlayerIps);

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
        } catch (Exception e) {
            log.warn("Failed to process 2FA verifications during sync", e);
        }

        try {
            Optional<MigrationStatus> activeMigration = migrationRepository.findActiveMigration(server);
            activeMigration.ifPresent(migration -> data.put("migrationTask", Map.of(
                "taskId", migration.getTaskId(),
                "type", migration.getType()
            )));
        } catch (Exception e) {
            log.warn("Failed to check active migration during sync", e);
        }

        if (serverStatus != null) {
            try {
                long epochSeconds = now.getEpochSecond();
                Date fiveMinBoundary = Date.from(Instant.ofEpochSecond((epochSeconds / 300) * 300));
                serverInstanceSnapshotRepository.upsertServerEntry(
                    fiveMinBoundary,
                    server.getId(),
                    serverName,
                    serverStatus.onlinePlayerCount(),
                    serverStatus.platformType(),
                    serverStatus.serverVersion(),
                    clientIp,
                    serverStatus.pluginVersion(),
                    Date.from(now)
                );
            } catch (Exception e) {
                log.warn("Failed to upsert server instance snapshot during sync", e);
            }
        }

        return Map.of(
            "timestamp", now.toString(),
            "data", data
        );
    }

    private void markOfflinePlayers(Server server, Set<String> onlineUuids, String serverName, Date logoutTime) {
        playerRepository.markStalePlayersOffline(server, onlineUuids, serverName, logoutTime);
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

            if (EnforcementCategory.BAN.name().equals(category) || EnforcementCategory.MUTE.name().equals(category)) {
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

    public Map<String, Object> syncV2(
        Server server,
        String lastSyncTimestamp,
        List<OnlinePlayerInput> onlinePlayers,
        String serverName,
        List<ChatLogInput> chatLogs,
        List<CommandLogInput> commandLogs,
        String clientIp
    ) {
        Map<String, Object> result = sync(
            server,
            lastSyncTimestamp,
            onlinePlayers,
            serverName,
            chatLogs,
            commandLogs,
            null,
            clientIp
        );

        convertUrlsToRelativePaths(result);

        return result;
    }

    @SuppressWarnings("unchecked")
    private void convertUrlsToRelativePaths(Map<String, Object> result) {
        Object dataObj = result.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return;
        }

        convertTicketUrlsInNotifications((List<Map<String, Object>>) data.get("staffNotifications"));
        convertTicketUrlsInNotifications((List<Map<String, Object>>) data.get("playerNotifications"));
    }

    @SuppressWarnings("unchecked")
    private void convertTicketUrlsInNotifications(List<Map<String, Object>> notifications) {
        if (notifications == null) {
            return;
        }

        for (Map<String, Object> notification : notifications) {
            Object dataObj = notification.get("data");
            if (!(dataObj instanceof Map<?, ?> rawData)) {
                continue;
            }
            Map<String, Object> data = (Map<String, Object>) rawData;
            Object ticketUrl = data.get("ticketUrl");
            if (ticketUrl instanceof String url) {
                data.put("ticketUrl", extractRelativePath(url));
            }
        }
    }

    private String extractRelativePath(String fullUrl) {
        try {
            return URI.create(fullUrl).getPath();
        } catch (Exception e) {
            int idx = fullUrl.indexOf("/", fullUrl.indexOf("://") + 3);
            return idx >= 0 ? fullUrl.substring(idx) : fullUrl;
        }
    }

    public record ServerStatusInput(int onlinePlayerCount, int maxPlayers, String serverVersion, String platformType, String pluginVersion) {
    }

    public record OnlinePlayerInput(String uuid, String username, String ipAddress) {
    }

    public record ChatLogInput(String uuid, String username, String message, long timestamp, String server) {
    }

    public record CommandLogInput(String uuid, String username, String command, long timestamp, String server) {
    }

    private static String normalizeUuid(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }
}
