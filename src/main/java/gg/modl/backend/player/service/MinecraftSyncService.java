package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.MigrationMongoRepository;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerInstanceSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerActivityRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModificationType;
import gg.modl.backend.player.data.punishment.EnforcementCategory;
import gg.modl.backend.player.dto.response.SimplePunishmentView;
import gg.modl.backend.player.dto.response.SyncDataView;
import gg.modl.backend.player.dto.response.SyncPunishmentEntry;
import gg.modl.backend.player.dto.response.SyncResult;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.infrastructure.util.UuidUtils;
import gg.modl.backend.staff.data.Staff;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinecraftSyncService {
    private final PlayerMongoRepository playerRepository;
    private final StaffMongoRepository staffRepository;
    private final ServerActivityRepository serverActivityRepository;
    private final MigrationMongoRepository migrationRepository;
    private final ServerInstanceSnapshotMongoRepository serverInstanceSnapshotRepository;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final PunishmentLifecycleService punishmentLifecycleService;
    private final MinecraftChatLogService minecraftChatLogService;
    private final IssuerNameResolver issuerNameResolver;
    private final SyncStaffEventService syncStaffEventService;
    private final SyncActiveStaffService syncActiveStaffService;

    public SyncResult sync(
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

        applyPresence(server, onlinePlayers, serverName, now);

        Instant lastSync = parseLastSync(lastSyncTimestamp, now);

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        List<SyncPunishmentEntry> pendingPunishments = new ArrayList<>();
        List<SyncPunishmentEntry> recentlyModifiedPunishments = new ArrayList<>();
        List<Map<String, Object>> playerNotifications = new ArrayList<>();

        Set<String> onlineUuids = collectOnlineUuids(onlinePlayers);

        if (!onlineUuids.isEmpty()) {
            List<Player> players = playerRepository.findByMinecraftUuids(server, onlineUuids);

            Map<String, String> resolvedIssuers = issuerNameResolver.resolveForPlayers(server, players);

            for (Player player : players) {
                List<String> promoted = punishmentLifecycleService.promoteUnstartedPunishments(server, player);
                if (!promoted.isEmpty()) {
                    player = playerRepository.findByMinecraftUuid(server, player.getMinecraftUuid().toString()).orElse(null);
                    if (player == null) {
                        continue;
                    }
                }

                collectPlayerPunishments(player, types, lastSync, resolvedIssuers, pendingPunishments, recentlyModifiedPunishments);
                collectPlayerNotifications(player, playerNotifications);
            }
        }

        pendingPunishments = deduplicatePendingPunishments(pendingPunishments);
        List<Map<String, Object>> staffNotifications = syncStaffEventService.collectStaffEvents(server, lastSync, types, recentlyModifiedPunishments);

        Map<String, String> onlinePlayerIps = new HashMap<>();
        if (onlinePlayers != null) {
            for (OnlinePlayerInput onlinePlayer : onlinePlayers) {
                if (onlinePlayer.uuid() != null && onlinePlayer.ipAddress() != null) {
                    onlinePlayerIps.put(UuidUtils.normalize(onlinePlayer.uuid()), onlinePlayer.ipAddress());
                }
            }
        }

        List<Map<String, Object>> activeStaffMembers = syncActiveStaffService.getActiveStaffMembers(server, onlinePlayerIps);

        submitLogs(server, chatLogs, commandLogs);

        Long staffPermissionsUpdatedAt = server.getStaffPermissionsUpdatedAt() != null
            ? server.getStaffPermissionsUpdatedAt().getTime()
            : null;
        Long punishmentTypesUpdatedAt = server.getPunishmentTypesUpdatedAt() != null
            ? server.getPunishmentTypesUpdatedAt().getTime()
            : null;
        List<Map<String, Object>> staff2faVerifications = collectStaff2faVerifications(server);
        Map<String, Object> migrationTask = collectMigrationTask(server);

        applyServerStatus(server, serverStatus, serverName, clientIp, now);

        SyncDataView data = new SyncDataView(
            pendingPunishments,
            List.of(),
            recentlyModifiedPunishments,
            playerNotifications,
            staffNotifications,
            activeStaffMembers,
            List.of(),
            staffPermissionsUpdatedAt,
            punishmentTypesUpdatedAt,
            staff2faVerifications,
            migrationTask
        );

        return new SyncResult(now.toString(), data);
    }

    private void collectPlayerPunishments(
        Player player,
        List<PunishmentType> types,
        Instant lastSync,
        Map<String, String> resolvedIssuers,
        List<SyncPunishmentEntry> pendingPunishments,
        List<SyncPunishmentEntry> recentlyModifiedPunishments
    ) {
        String uuid = player.getMinecraftUuid().toString();
        String username = PlayerDataUtils.extractLatestUsername(player.getUsernames());

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
                recentlyModifiedPunishments.add(new SyncPunishmentEntry(
                    uuid, username,
                    PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)));
            }

            boolean recentlyPardoned = punishment.getModifications()
                .stream()
                .anyMatch(mod -> mod.date() != null
                                 && mod.date().toInstant().isAfter(lastSync)
                                 && PunishmentModificationType.isPardon(mod.type()));
            if (recentlyPardoned && category != null) {
                pardonedCategories.add(category);
            }

            if (punishment.getTypeOrdinal() == 0 && punishment.getStarted() == null) {
                pendingPunishments.add(new SyncPunishmentEntry(
                    uuid, username,
                    PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)));
                continue;
            }

            if (!active) {
                continue;
            }

            if (punishment.getStarted() != null
                && punishment.getIssued() != null
                && punishment.getIssued().toInstant().isAfter(lastSync)
                && category != null) {
                pendingPunishments.add(new SyncPunishmentEntry(
                    uuid, username,
                    PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)));
            }

            if (category != null && punishment.getStarted() != null) {
                categoriesWithActiveStarted.add(category);
            } else if (category != null && punishment.getStarted() == null) {
                Punishment existing = oldestUnstartedPerCategory.get(category);
                if (existing == null
                    || (punishment.getIssued() != null
                        && (existing.getIssued() == null
                            || punishment.getIssued().before(existing.getIssued())))) {
                    oldestUnstartedPerCategory.put(category, punishment);
                }
            }
        }

        for (Map.Entry<String, Punishment> entry : oldestUnstartedPerCategory.entrySet()) {
            if (!categoriesWithActiveStarted.contains(entry.getKey())) {
                pendingPunishments.add(new SyncPunishmentEntry(
                    uuid, username,
                    PunishmentMapper.toSimplePunishment(entry.getValue(), types, statusCalculator, resolvedIssuers)));
            }
        }

        if (!pardonedCategories.isEmpty()) {
            for (Punishment punishment : player.getPunishments()) {
                if (!statusCalculator.isPunishmentActive(punishment) || punishment.getStarted() == null) {
                    continue;
                }

                String category = statusCalculator.getEffectiveCategory(punishment, types);
                if (category != null && pardonedCategories.contains(category)) {
                    pendingPunishments.add(new SyncPunishmentEntry(
                        uuid, username,
                        PunishmentMapper.toSimplePunishment(punishment, types, statusCalculator, resolvedIssuers)));
                    pardonedCategories.remove(category);
                }
            }
        }
    }

    private void collectPlayerNotifications(Player player, List<Map<String, Object>> playerNotifications) {
        String uuid = player.getMinecraftUuid().toString();
        for (Map<String, Object> notification : player.data().pendingNotifications()) {
            Map<String, Object> payload = new HashMap<>(notification);
            payload.put("targetPlayerUuid", uuid);
            playerNotifications.add(payload);
        }
    }

    private List<Map<String, Object>> collectStaff2faVerifications(Server server) {
        try {
            List<Staff> pendingStaff = staffRepository.findWithPendingTwoFactorDelivery(server);
            if (!pendingStaff.isEmpty()) {
                List<Map<String, Object>> verifications = pendingStaff.stream()
                    .map(staff -> Map.<String, Object>of("minecraftUuid", staff.getAssignedMinecraftUuid()))
                    .toList();
                staffRepository.clearPendingTwoFactorDelivery(server);
                return verifications;
            }
        } catch (Exception e) {
            log.warn("Failed to process 2FA verifications during sync", e);
        }
        return null;
    }

    private Map<String, Object> collectMigrationTask(Server server) {
        try {
            Optional<MigrationStatus> activeMigration = migrationRepository.findActiveMigration(server);
            if (activeMigration.isPresent()) {
                MigrationStatus migration = activeMigration.get();
                return Map.of(
                    "taskId", migration.getTaskId(),
                    "type", migration.getType()
                );
            }
        } catch (Exception e) {
            log.warn("Failed to check active migration during sync", e);
        }
        return null;
    }

    public void applyPresence(Server server, List<OnlinePlayerInput> onlinePlayers, String serverName, Instant now) {
        serverActivityRepository.updateActivityAndPlayerCount(server, Date.from(now),
            onlinePlayers != null ? (long) onlinePlayers.size() : 0L);

        playerRepository.markStalePlayersOffline(server, collectOnlineUuids(onlinePlayers), serverName, Date.from(now));
    }

    public void submitLogs(Server server, List<ChatLogInput> chatLogs, List<CommandLogInput> commandLogs) {
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
    }

    public void applyServerStatus(Server server, ServerStatusInput serverStatus, String serverName, String clientIp, Instant now) {
        if (serverStatus == null) {
            return;
        }
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

    private Instant parseLastSync(String lastSyncTimestamp, Instant now) {
        if (lastSyncTimestamp == null || lastSyncTimestamp.isBlank()) {
            return now.minusSeconds(30);
        }
        try {
            return Instant.parse(lastSyncTimestamp);
        } catch (DateTimeParseException e) {
            log.warn("Invalid lastSyncTimestamp '{}' during sync; falling back to default window", lastSyncTimestamp);
            return now.minusSeconds(30);
        }
    }

    private Set<String> collectOnlineUuids(List<OnlinePlayerInput> onlinePlayers) {
        Set<String> onlineUuids = new HashSet<>();
        if (onlinePlayers != null) {
            for (OnlinePlayerInput onlinePlayer : onlinePlayers) {
                if (onlinePlayer.uuid() != null) {
                    onlineUuids.add(UuidUtils.normalize(onlinePlayer.uuid()));
                }
            }
        }
        return onlineUuids;
    }

    private List<SyncPunishmentEntry> deduplicatePendingPunishments(List<SyncPunishmentEntry> punishments) {
        Map<String, SyncPunishmentEntry> oldestByPlayerCategory = new LinkedHashMap<>();
        List<SyncPunishmentEntry> result = new ArrayList<>();

        for (SyncPunishmentEntry entry : punishments) {
            SimplePunishmentView punishment = entry.punishment();
            String category = punishment.category();
            if (EnforcementCategory.BAN.name().equals(category) || EnforcementCategory.MUTE.name().equals(category)) {
                String key = entry.minecraftUuid() + "|" + category;
                SyncPunishmentEntry existing = oldestByPlayerCategory.get(key);
                if (existing == null) {
                    oldestByPlayerCategory.put(key, entry);
                } else if (punishment.issuedAt() < existing.punishment().issuedAt()) {
                    oldestByPlayerCategory.put(key, entry);
                }
            } else {
                result.add(entry);
            }
        }

        result.addAll(oldestByPlayerCategory.values());
        return result;
    }

    public SyncResult syncV2(
        Server server,
        String lastSyncTimestamp,
        List<OnlinePlayerInput> onlinePlayers,
        String serverName,
        List<ChatLogInput> chatLogs,
        List<CommandLogInput> commandLogs,
        String clientIp
    ) {
        SyncResult result = sync(
            server,
            lastSyncTimestamp,
            onlinePlayers,
            serverName,
            chatLogs,
            commandLogs,
            null,
            clientIp
        );

        convertTicketUrlsInNotifications(result.data().staffNotifications());
        convertTicketUrlsInNotifications(result.data().playerNotifications());

        return result;
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
}
