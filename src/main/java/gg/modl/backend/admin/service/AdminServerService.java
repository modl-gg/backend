package gg.modl.backend.admin.service;

import com.mongodb.client.result.DeleteResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import gg.modl.backend.server.service.ServerProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServerService {
    private static final long USAGE_STATS_TTL_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_USAGE_BATCH_SIZE = 50;

    private final DynamicMongoTemplateProvider mongoProvider;
    private final ServerProvisioningService provisioningService;

    private MongoTemplate getTemplate() {
        return mongoProvider.getGlobalDatabase();
    }

    public List<Server> findServers(String search, String plan, String status, String sortField, String sortOrder, int skip, int limit) {
        Query query = buildFilterQuery(search, plan, status);

        // Validate and set sort
        List<String> allowedSortFields = Arrays.asList("serverName", "customDomain", "adminEmail", "plan", "createdAt", "updatedAt", "userCount", "provisioningStatus", "lastActivityAt");
        String field = allowedSortFields.contains(sortField) ? sortField : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;

        query.with(Sort.by(direction, field));
        query.skip(skip).limit(limit);
        query.fields()
                .include("serverName")
                .include("customDomain")
                .include("adminEmail")
                .include("plan")
                .include("emailVerified")
                .include("provisioningStatus")
                .include("createdAt")
                .include("updatedAt")
                .include("userCount")
                .include("ticketCount")
                .include("lastActivityAt");

        return getTemplate().find(query, Server.class, CollectionName.MODL_SERVERS);
    }

    public long countServers(String search, String plan, String status) {
        Query query = buildFilterQuery(search, plan, status);
        return getTemplate().count(query, Server.class, CollectionName.MODL_SERVERS);
    }

    public void refreshUsageStatsForActiveServers(int maxServers) {
        int boundedLimit = Math.max(1, Math.min(maxServers, 500));
        Date now = new Date();
        Date staleCutoff = new Date(now.getTime() - USAGE_STATS_TTL_MILLIS);

        Criteria staleCriteria = new Criteria().orOperator(
                Criteria.where("lastStatsUpdatedAt").exists(false),
                Criteria.where("lastStatsUpdatedAt").lt(staleCutoff)
        );

        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("databaseName").ne(null),
                Criteria.where("provisioningStatus").is(ProvisioningStatus.COMPLETED),
                Criteria.where("emailVerified").is(true),
                staleCriteria
        ));
        query.with(Sort.by(Sort.Direction.ASC, "lastStatsUpdatedAt"));
        query.limit(boundedLimit);
        query.fields()
                .include("databaseName")
                .include("userCount")
                .include("ticketCount")
                .include("lastStatsUpdatedAt")
                .include("lastActivityAt")
                .include("updatedAt");

        List<Server> servers = getTemplate().find(query, Server.class, CollectionName.MODL_SERVERS);
        for (Server server : servers) {
            getOrComputeUsageStats(server, now, false);
        }
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

        Query query = Query.query(Criteria.where("_id").in(filteredIds));
        query.fields()
                .include("databaseName")
                .include("userCount")
                .include("ticketCount")
                .include("lastStatsUpdatedAt")
                .include("lastActivityAt")
                .include("updatedAt");

        Date now = new Date();
        List<Server> servers = getTemplate().find(query, Server.class, CollectionName.MODL_SERVERS);
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

    private Query buildFilterQuery(String search, String plan, String status) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            String sanitizedSearch = Pattern.quote(search.trim());
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("serverName").regex(sanitizedSearch, "i"),
                    Criteria.where("customDomain").regex(sanitizedSearch, "i"),
                    Criteria.where("adminEmail").regex(sanitizedSearch, "i")
            );
            criteriaList.add(searchCriteria);
        }

        if (plan != null && !"all".equals(plan)) {
            try {
                criteriaList.add(Criteria.where("plan").is(ServerPlan.valueOf(plan.trim().toUpperCase(Locale.ROOT))));
            } catch (IllegalArgumentException ignored) {
                criteriaList.add(Criteria.where("plan").is("__invalid_plan__"));
            }
        }

        if (status != null && !"all".equals(status)) {
            switch (status) {
                case "active" -> {
                    criteriaList.add(Criteria.where("provisioningStatus").is(ProvisioningStatus.COMPLETED));
                    criteriaList.add(Criteria.where("emailVerified").is(true));
                }
                case "pending" -> criteriaList.add(Criteria.where("provisioningStatus").in(
                        ProvisioningStatus.PENDING,
                        ProvisioningStatus.IN_PROGRESS
                ));
                case "failed" -> criteriaList.add(Criteria.where("provisioningStatus").is(ProvisioningStatus.FAILED));
                case "unverified" -> criteriaList.add(Criteria.where("emailVerified").is(false));
            }
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        return query;
    }

    public Optional<Server> findById(String id) {
        return Optional.ofNullable(getTemplate().findById(id, Server.class, CollectionName.MODL_SERVERS));
    }

    public Server save(Server server) {
        return getTemplate().save(server, CollectionName.MODL_SERVERS);
    }

    private static final Set<String> ALLOWED_UPDATE_FIELDS = Set.of(
            "adminEmail", "emailVerified", "provisioningStatus", "provisioningNotes",
            "plan", "subscriptionStatus", "lastActivityAt", "updatedAt"
    );

    public Server updateById(String id, Map<String, Object> updateData) {
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update();
        updateData.forEach((key, value) -> {
            if (ALLOWED_UPDATE_FIELDS.contains(key)) {
                if ("plan".equals(key) && value instanceof String planValue) {
                    update.set(key, ServerPlan.valueOf(planValue.trim().toUpperCase(Locale.ROOT)));
                } else if ("provisioningStatus".equals(key) && value instanceof String provisioningStatusValue) {
                    update.set(key, ProvisioningStatus.valueOf(provisioningStatusValue.trim().toUpperCase(Locale.ROOT)));
                } else if ("subscriptionStatus".equals(key) && value instanceof String subscriptionStatusValue) {
                    update.set(key, SubscriptionStatus.valueOf(subscriptionStatusValue.trim().toUpperCase(Locale.ROOT)));
                } else {
                    update.set(key, value);
                }
            }
        });
        getTemplate().updateFirst(query, update, Server.class, CollectionName.MODL_SERVERS);
        return getTemplate().findById(id, Server.class, CollectionName.MODL_SERVERS);
    }

    public boolean deleteById(String id) {
        Query query = Query.query(Criteria.where("_id").is(id));
        DeleteResult result = getTemplate().remove(query, Server.class, CollectionName.MODL_SERVERS);
        return result.getDeletedCount() > 0;
    }

    public long bulkDelete(List<String> serverIds) {
        Query query = Query.query(Criteria.where("_id").in(serverIds));
        return getTemplate().remove(query, Server.class, CollectionName.MODL_SERVERS).getDeletedCount();
    }

    public long bulkSuspend(List<String> serverIds) {
        Query query = Query.query(Criteria.where("_id").in(serverIds));
        Update update = new Update()
                .set("provisioningStatus", ProvisioningStatus.FAILED)
                .set("updatedAt", new Date());
        return getTemplate().updateMulti(query, update, Server.class, CollectionName.MODL_SERVERS).getModifiedCount();
    }

    public long bulkActivate(List<String> serverIds) {
        Query query = Query.query(Criteria.where("_id").in(serverIds));
        Update update = new Update()
                .set("provisioningStatus", ProvisioningStatus.COMPLETED)
                .set("emailVerified", true)
                .set("updatedAt", new Date());
        long modified = getTemplate().updateMulti(query, update, Server.class, CollectionName.MODL_SERVERS).getModifiedCount();

        // Seed default data for each activated server
        for (String id : serverIds) {
            try {
                Server server = getTemplate().findById(id, Server.class, CollectionName.MODL_SERVERS);
                if (server != null && server.getDatabaseName() != null) {
                    provisioningService.provision(server);
                }
            } catch (Exception e) {
                log.warn("Failed to provision server {}: {}", id, e.getMessage());
            }
        }

        return modified;
    }

    public long bulkUpdatePlan(List<String> serverIds, String plan) {
        Query query = Query.query(Criteria.where("_id").in(serverIds));
        ServerPlan parsedPlan = ServerPlan.valueOf(plan.trim().toUpperCase(Locale.ROOT));
        Update update = new Update()
                .set("plan", parsedPlan)
                .set("updatedAt", new Date());
        return getTemplate().updateMulti(query, update, Server.class, CollectionName.MODL_SERVERS).getModifiedCount();
    }

    public Map<String, Object> getServerStats(Server server) {
        Map<String, Object> stats = new HashMap<>();
        Date now = new Date();

        if (server.getDatabaseName() == null) {
            long cachedUsers = server.getUserCount() != null ? server.getUserCount() : 0L;
            long cachedTickets = server.getTicketCount() != null ? server.getTicketCount() : 0L;
            stats.put("totalPlayers", cachedUsers);
            stats.put("totalTickets", cachedTickets);
            stats.put("totalLogs", 0);
            stats.put("lastActivity", server.getUpdatedAt());
            stats.put("databaseSize", 0);
            return stats;
        }

        try {
            MongoTemplate serverDb = mongoProvider.getFromDatabaseName(server.getDatabaseName());

            long players = serverDb.count(new Query(), "players");
            long tickets = serverDb.count(new Query(), "tickets");
            long logs = serverDb.count(new Query(), "logs");

            persistUsageStats(server.getId(), players, tickets, now);

            stats.put("totalPlayers", players);
            stats.put("totalTickets", tickets);
            stats.put("totalLogs", logs);
            stats.put("lastActivity", server.getLastActivityAt() != null ? server.getLastActivityAt() : server.getUpdatedAt());

            // Get database size
            Document dbStats = serverDb.getDb().runCommand(new Document("dbStats", 1));
            stats.put("databaseSize", dbStats.get("storageSize", 0L));

        } catch (Exception e) {
            log.warn("Failed to get stats for server {}: {}", server.getServerName(), e.getMessage());
            long cachedUsers = server.getUserCount() != null ? server.getUserCount() : 0L;
            long cachedTickets = server.getTicketCount() != null ? server.getTicketCount() : 0L;
            stats.put("totalPlayers", cachedUsers);
            stats.put("totalTickets", cachedTickets);
            stats.put("totalLogs", 0);
            stats.put("lastActivity", server.getUpdatedAt());
            stats.put("databaseSize", 0);
        }

        return stats;
    }

    public void resetServerDatabase(Server server) {
        if (server.getDatabaseName() != null) {
            try {
                MongoTemplate serverDb = mongoProvider.getFromDatabaseName(server.getDatabaseName());
                serverDb.getDb().drop();
                log.info("Dropped database {} for server {}", server.getDatabaseName(), server.getServerName());
            } catch (Exception e) {
                log.warn("Failed to drop database {}: {}", server.getDatabaseName(), e.getMessage());
            }
        }

        // Reset server to pending state
        Query query = Query.query(Criteria.where("_id").is(server.getId()));
        Update update = new Update()
                .set("provisioningStatus", ProvisioningStatus.PENDING)
                .set("provisioningNotes", "Database reset - awaiting reprovisioning")
                .unset("lastActivityAt")
                .unset("customDomainStatus")
                .unset("customDomainLastChecked")
                .unset("customDomainError")
                .set("updatedAt", new Date());
        getTemplate().updateFirst(query, update, Server.class, CollectionName.MODL_SERVERS);
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

        try {
            MongoTemplate serverDb = mongoProvider.getFromDatabaseName(server.getDatabaseName());
            long players = serverDb.count(new Query(), "players");
            long tickets = serverDb.count(new Query(), "tickets");
            persistUsageStats(server.getId(), players, tickets, now);
            return new ComputedUsage(players, tickets, now, false);
        } catch (Exception e) {
            log.warn("Failed to refresh usage stats for server {}: {}", server.getServerName(), e.getMessage());
            Date updatedAt = server.getLastStatsUpdatedAt() != null ? server.getLastStatsUpdatedAt() : now;
            return new ComputedUsage(cachedUsers, cachedTickets, updatedAt, true);
        }
    }

    private boolean isUsageStatsCacheFresh(Server server, Date now) {
        if (server.getLastStatsUpdatedAt() == null) {
            return false;
        }

        long ageMillis = now.getTime() - server.getLastStatsUpdatedAt().getTime();
        return ageMillis >= 0 && ageMillis <= USAGE_STATS_TTL_MILLIS;
    }

    private void persistUsageStats(String serverId, long userCount, long ticketCount, Date updatedAt) {
        Query query = Query.query(Criteria.where("_id").is(serverId));
        Update update = new Update()
                .set("userCount", userCount)
                .set("ticketCount", ticketCount)
                .set("lastStatsUpdatedAt", updatedAt);
        getTemplate().updateFirst(query, update, Server.class, CollectionName.MODL_SERVERS);
    }

    private record ComputedUsage(long userCount, long ticketCount, Date updatedAt, boolean fromCache) {}

    public record UsageSummary(long userCount, long ticketCount, Date updatedAt, boolean fromCache) {}
}
