package gg.modl.backend.admin.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServerService {
    private final DynamicMongoTemplateProvider mongoProvider;

    private MongoTemplate getTemplate() {
        return mongoProvider.getGlobalDatabase();
    }

    public List<Server> findServers(String search, String plan, String status, String sortField, String sortOrder, int skip, int limit) {
        Query query = buildFilterQuery(search, plan, status);

        // Validate and set sort
        List<String> allowedSortFields = Arrays.asList("serverName", "customDomain", "adminEmail", "plan", "createdAt", "updatedAt", "userCount", "ticketCount", "provisioningStatus", "lastActivityAt");
        String field = allowedSortFields.contains(sortField) ? sortField : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;

        query.with(Sort.by(direction, field));
        query.skip(skip).limit(limit);
        query.fields().exclude("emailVerificationToken", "provisioningSignInToken");

        return getTemplate().find(query, Server.class, CollectionName.MODL_SERVERS);
    }

    public long countServers(String search, String plan, String status) {
        Query query = buildFilterQuery(search, plan, status);
        return getTemplate().count(query, Server.class, CollectionName.MODL_SERVERS);
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
            criteriaList.add(Criteria.where("plan").is(plan));
        }

        if (status != null && !"all".equals(status)) {
            switch (status) {
                case "active" -> {
                    criteriaList.add(Criteria.where("provisioningStatus").is("completed"));
                    criteriaList.add(Criteria.where("emailVerified").is(true));
                }
                case "pending" -> criteriaList.add(Criteria.where("provisioningStatus").in("pending", "in-progress"));
                case "failed" -> criteriaList.add(Criteria.where("provisioningStatus").is("failed"));
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
                update.set(key, value);
            }
        });
        getTemplate().updateFirst(query, update, Server.class, CollectionName.MODL_SERVERS);
        return getTemplate().findById(id, Server.class, CollectionName.MODL_SERVERS);
    }

    public boolean deleteById(String id) {
        Query query = Query.query(Criteria.where("_id").is(id));
        var result = getTemplate().remove(query, Server.class, CollectionName.MODL_SERVERS);
        return result.getDeletedCount() > 0;
    }

    public long bulkDelete(List<String> serverIds) {
        Query query = Query.query(Criteria.where("_id").in(serverIds));
        return getTemplate().remove(query, Server.class, CollectionName.MODL_SERVERS).getDeletedCount();
    }

    public long bulkSuspend(List<String> serverIds) {
        Query query = Query.query(Criteria.where("_id").in(serverIds));
        Update update = new Update()
                .set("provisioningStatus", ProvisioningStatus.failed)
                .set("updatedAt", new Date());
        return getTemplate().updateMulti(query, update, Server.class, CollectionName.MODL_SERVERS).getModifiedCount();
    }

    public long bulkActivate(List<String> serverIds) {
        Query query = Query.query(Criteria.where("_id").in(serverIds));
        Update update = new Update()
                .set("provisioningStatus", ProvisioningStatus.completed)
                .set("emailVerified", true)
                .set("updatedAt", new Date());
        return getTemplate().updateMulti(query, update, Server.class, CollectionName.MODL_SERVERS).getModifiedCount();
    }

    public long bulkUpdatePlan(List<String> serverIds, String plan) {
        Query query = Query.query(Criteria.where("_id").in(serverIds));
        Update update = new Update()
                .set("plan", plan)
                .set("updatedAt", new Date());
        return getTemplate().updateMulti(query, update, Server.class, CollectionName.MODL_SERVERS).getModifiedCount();
    }

    public Map<String, Object> getServerStats(Server server) {
        Map<String, Object> stats = new HashMap<>();

        if (server.getDatabaseName() == null) {
            stats.put("totalPlayers", 0);
            stats.put("totalTickets", 0);
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

            stats.put("totalPlayers", players);
            stats.put("totalTickets", tickets);
            stats.put("totalLogs", logs);
            stats.put("lastActivity", server.getLastActivityAt() != null ? server.getLastActivityAt() : server.getUpdatedAt());

            // Get database size
            Document dbStats = serverDb.getDb().runCommand(new Document("dbStats", 1));
            stats.put("databaseSize", dbStats.get("storageSize", 0L));

        } catch (Exception e) {
            log.warn("Failed to get stats for server {}: {}", server.getServerName(), e.getMessage());
            stats.put("totalPlayers", 0);
            stats.put("totalTickets", 0);
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
                .set("provisioningStatus", ProvisioningStatus.pending)
                .set("provisioningNotes", "Database reset - awaiting reprovisioning")
                .unset("lastActivityAt")
                .unset("customDomain_status")
                .unset("customDomain_lastChecked")
                .unset("customDomain_error")
                .set("updatedAt", new Date());
        getTemplate().updateFirst(query, update, Server.class, CollectionName.MODL_SERVERS);
    }
}
