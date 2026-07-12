package gg.modl.backend.admin.service;

import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.database.mongo.repository.ServerAdminRepository;
import gg.modl.backend.database.mongo.repository.ServerDatabaseMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerProvisioningRepository;
import gg.modl.backend.database.mongo.repository.ServerUsageRepository;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.util.CsvUtil;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import gg.modl.backend.server.service.ServerProvisioningService;
import gg.modl.backend.staff.service.StaffService;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServerService {
    private final ServerAdminRepository serverAdminRepository;
    private final ServerProvisioningRepository serverProvisioningRepository;
    private final ServerUsageRepository serverUsageRepository;
    private final ServerDatabaseMongoRepository serverDatabaseRepository;
    private final ServerProvisioningService provisioningService;
    private final ServerService serverService;
    private final StaffService staffService;
    private static final long USAGE_STATS_TTL_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_USAGE_BATCH_SIZE = 50;

    public long countServers(String search, String plan, String status) {
        return serverAdminRepository.countAdminServers(search, plan, status);
    }

    @Async
    public void refreshUsageStatsForActiveServers(int maxServers) {
        int boundedLimit = Math.max(1, Math.min(maxServers, 500));
        Date now = new Date();
        Date staleCutoff = new Date(now.getTime() - USAGE_STATS_TTL_MILLIS);

        List<Server> servers = serverUsageRepository.findUsageRefreshCandidates(staleCutoff, boundedLimit);
        for (Server server : servers) {
            getOrComputeUsageStats(server, now, false);
        }
    }

    private ComputedUsage getOrComputeUsageStats(Server server, Date now, boolean forceRefresh) {
        long cachedUsers = server.getUserCount() != null ? server.getUserCount() : 0L;
        long cachedTickets = server.getTicketCount() != null ? server.getTicketCount() : 0L;

        if (!forceRefresh && isUsageStatsCacheFresh(server, now)) {
            return new ComputedUsage(cachedUsers, cachedTickets, server.getLastStatsUpdatedAt(), true);
        }

        if (server.getDatabaseName() == null || server.getDatabaseName().isBlank()) {
            persistUsageStats(server.getId(), cachedUsers, cachedTickets, now);
            return new ComputedUsage(cachedUsers, cachedTickets, now, false);
        }

        Optional<ServerDatabaseMongoRepository.UsageCounts> usageCounts = serverDatabaseRepository.readUsageCounts(server);
        if (usageCounts.isPresent()) {
            persistUsageStats(server.getId(), usageCounts.get().players(), usageCounts.get().tickets(), now);
            return new ComputedUsage(usageCounts.get().players(), usageCounts.get().tickets(), now, false);
        }

        log.warn("Failed to refresh usage stats for server {}", server.getServerName());
        Date updatedAt = server.getLastStatsUpdatedAt() != null ? server.getLastStatsUpdatedAt() : now;
        return new ComputedUsage(cachedUsers, cachedTickets, updatedAt, true);
    }

    private boolean isUsageStatsCacheFresh(Server server, Date now) {
        if (server.getLastStatsUpdatedAt() == null) {
            return false;
        }

        long ageMillis = now.getTime() - server.getLastStatsUpdatedAt().getTime();
        return ageMillis >= 0 && ageMillis <= USAGE_STATS_TTL_MILLIS;
    }

    private void persistUsageStats(String serverId, long userCount, long ticketCount, Date updatedAt) {
        serverUsageRepository.updateUsageStats(serverId, userCount, ticketCount, updatedAt);
    }

    public Map<String, UsageSummary> getUsageStatsForServerIds(List<String> serverIds, boolean forceRefresh) {
        if (serverIds == null || serverIds.isEmpty()) {
            return Map.of();
        }

        List<String> filteredIds = serverIds.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .distinct()
            .limit(MAX_USAGE_BATCH_SIZE)
            .toList();

        if (filteredIds.isEmpty()) {
            return Map.of();
        }

        Date now = new Date();
        List<Server> servers = serverUsageRepository.findUsageTargetsByIds(filteredIds);
        Map<String, UsageSummary> usageByServerId = new HashMap<>();

        for (Server server : servers) {
            ComputedUsage usage = getOrComputeUsageStats(server, now, forceRefresh);
            usageByServerId.put(server.getId(), new UsageSummary(
                usage.userCount(),
                usage.ticketCount(),
                usage.updatedAt(),
                usage.fromCache()
            ));
        }

        return usageByServerId;
    }

    public Server createServer(String serverName, String customDomain, String adminEmail, String plan) {
        ServerPlan serverPlan = plan != null ? ServerPlan.valueOf(plan.trim().toUpperCase(Locale.ROOT)) : ServerPlan.FREE;
        return serverService.createServer(serverName, customDomain, adminEmail, null, serverPlan);
    }

    public void changeAdminEmail(Server server, String newAdminEmail) {
        String normalizedEmail = EmailAddressUtil.normalizeIfValid(newAdminEmail);
        if (normalizedEmail == null) {
            throw new ValidationException("A valid admin email is required");
        }
        if (serverService.isAdminEmailInUse(normalizedEmail, server.getId())) {
            throw new ValidationException("Admin email is already in use by another server");
        }
        String previousAdminEmail = server.getAdminEmail();
        serverService.changeAdminEmail(server, normalizedEmail);
        if (previousAdminEmail != null && !previousAdminEmail.equalsIgnoreCase(normalizedEmail)
            && server.getDatabaseName() != null && !server.getDatabaseName().isBlank()) {
            staffService.offboardPreviousAdminEmail(server, previousAdminEmail);
        }
    }

    public String exportServersCsv(String plan, String status) {
        List<Server> servers = findServers(null, plan, status, "createdAt", "desc", 0, 10000);
        StringBuilder csv = new StringBuilder();
        csv.append(CsvUtil.row("id", "serverName", "customDomain", "adminEmail", "plan", "provisioningStatus", "emailVerified", "createdAt"));
        for (Server s : servers) {
            csv.append(CsvUtil.row(
                s.getId(), s.getServerName(), s.getCustomDomain(), s.getAdminEmail(),
                s.getPlan(), s.getProvisioningStatus(), s.getEmailVerified(), s.getCreatedAt()));
        }
        return csv.toString();
    }

    public List<Server> findServers(String search, String plan, String status, String sortField, String sortOrder, int skip, int limit) {
        return serverAdminRepository.findAdminServers(search, plan, status, sortField, sortOrder, skip, limit);
    }

    public Optional<Server> findById(String id) {
        return serverAdminRepository.findById(id);
    }

    public Server updateById(String id, Map<String, Object> updateData) {
        Update update = buildAllowedFieldsUpdate(updateData);
        return serverAdminRepository.applyFieldUpdate(id, update).orElse(null);
    }

    private Update buildAllowedFieldsUpdate(Map<String, Object> updateData) {
        Update update = new Update();
        for (Map.Entry<String, Object> entry : updateData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            switch (key) {
                case ServerFields.ADMIN_EMAIL -> update.set(ServerFields.ADMIN_EMAIL, value);
                case ServerFields.EMAIL_VERIFIED -> update.set(ServerFields.EMAIL_VERIFIED, value);
                case ServerFields.PROVISIONING_STATUS -> update.set(ServerFields.PROVISIONING_STATUS, normalizeProvisioningStatus(value));
                case ServerFields.PROVISIONING_NOTES -> update.set(ServerFields.PROVISIONING_NOTES, value);
                case ServerFields.PLAN -> update.set(ServerFields.PLAN, normalizePlan(value));
                case ServerFields.SUBSCRIPTION_STATUS -> update.set(ServerFields.SUBSCRIPTION_STATUS, normalizeSubscriptionStatus(value));
                case ServerFields.LAST_ACTIVITY_AT -> update.set(ServerFields.LAST_ACTIVITY_AT, normalizeDate(value));
                case ServerFields.UPDATED_AT -> update.set(ServerFields.UPDATED_AT, normalizeDate(value));
                default -> {
                }
            }
        }
        return update;
    }

    private ServerPlan normalizePlan(Object value) {
        if (value instanceof ServerPlan plan) {
            return plan;
        }
        return ServerPlan.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
    }

    private ProvisioningStatus normalizeProvisioningStatus(Object value) {
        if (value instanceof ProvisioningStatus provisioningStatus) {
            return provisioningStatus;
        }
        return ProvisioningStatus.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
    }

    private SubscriptionStatus normalizeSubscriptionStatus(Object value) {
        if (value instanceof SubscriptionStatus subscriptionStatus) {
            return subscriptionStatus;
        }
        return SubscriptionStatus.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
    }

    private Date normalizeDate(Object value) {
        if (value instanceof Date d) {
            return d;
        }
        if (value instanceof Instant i) {
            return Date.from(i);
        }
        if (value instanceof Number n) {
            return new Date(n.longValue());
        }
        if (value instanceof String s) {
            return Date.from(Instant.parse(s.trim()));
        }
        throw new IllegalArgumentException("Unsupported value type for date field: "
            + (value == null ? "null" : value.getClass()));
    }

    public boolean deleteById(String id) {
        return serverAdminRepository.deleteByServerId(id);
    }

    public long bulkDelete(List<String> serverIds) {
        return serverAdminRepository.deleteByServerIds(serverIds);
    }

    public long bulkSuspend(List<String> serverIds) {
        return serverAdminRepository.bulkSuspend(serverIds, new Date());
    }

    public long bulkActivate(List<String> serverIds) {
        long modified = serverAdminRepository.bulkActivate(serverIds, new Date());

        List<Server> servers = serverProvisioningRepository.findProvisioningCandidatesByIds(serverIds);
        for (Server server : servers) {
            if (server.getDatabaseName() == null) {
                continue;
            }
            try {
                provisioningService.provision(server);
                serverProvisioningRepository.markProvisioningCompleted(server.getId());
            } catch (Exception e) {
                log.warn("Failed to provision server {}", server.getId(), e);
                serverProvisioningRepository.markProvisioningFailed(server.getId(), Server.boundProvisioningNotes("Admin reprovision failed."));
            }
        }

        serverService.evictAllServerCaches();

        return modified;
    }

    public long bulkUpdatePlan(List<String> serverIds, String plan) {
        ServerPlan parsedPlan = ServerPlan.valueOf(plan.trim().toUpperCase(Locale.ROOT));
        return serverAdminRepository.bulkUpdatePlan(serverIds, parsedPlan, new Date());
    }

    public Map<String, Object> getServerStats(Server server) {
        Map<String, Object> stats = new HashMap<>();
        Date now = new Date();

        if (server.getDatabaseName() == null || server.getDatabaseName().isBlank()) {
            putCachedStats(stats, server);
            return stats;
        }

        Optional<ServerDatabaseMongoRepository.ServerDatabaseStats> databaseStats = serverDatabaseRepository.readStats(server);
        if (databaseStats.isPresent()) {
            ServerDatabaseMongoRepository.ServerDatabaseStats loadedStats = databaseStats.get();
            persistUsageStats(server.getId(), loadedStats.players(), loadedStats.tickets(), now);

            stats.put("totalPlayers", loadedStats.players());
            stats.put("totalTickets", loadedStats.tickets());
            stats.put("totalLogs", loadedStats.logs());
            stats.put("lastActivity", server.getLastActivityAt() != null ? server.getLastActivityAt() : server.getUpdatedAt());
            stats.put("databaseSize", loadedStats.storageSize());
            return stats;
        }

        log.warn("Failed to get stats for server {}", server.getServerName());
        putCachedStats(stats, server);
        return stats;
    }

    private void putCachedStats(Map<String, Object> stats, Server server) {
        long cachedUsers = server.getUserCount() != null ? server.getUserCount() : 0L;
        long cachedTickets = server.getTicketCount() != null ? server.getTicketCount() : 0L;
        stats.put("totalPlayers", cachedUsers);
        stats.put("totalTickets", cachedTickets);
        stats.put("totalLogs", 0);
        stats.put("lastActivity", server.getUpdatedAt());
        stats.put("databaseSize", 0);
    }

    public void resetServerDatabase(Server server) {
        if (server.getDatabaseName() != null) {
            if (serverDatabaseRepository.dropDatabase(server)) {
                log.info("Dropped database {} for server {}", server.getDatabaseName(), server.getServerName());
            } else {
                log.warn("Failed to drop database {}", server.getDatabaseName());
            }
        }

        serverProvisioningRepository.resetAfterDatabaseDrop(server.getId(), new Date());
    }

    private record ComputedUsage(long userCount, long ticketCount, Date updatedAt, boolean fromCache) {}

    public record UsageSummary(long userCount, long ticketCount, Date updatedAt, boolean fromCache) {}
}
