package gg.modl.backend.admin.service;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.admin.data.SystemLog;
import gg.modl.backend.admin.dto.request.CreateSystemLogRequest;
import gg.modl.backend.admin.dto.request.ResolveLogRequest;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.MongoUpdates;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.database.mongo.fields.SystemLogFields;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.SystemLogMongoRepository;
import gg.modl.backend.server.data.ProvisioningStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMonitoringService {
    private final SystemLogMongoRepository systemLogRepository;
    private final ServerMongoRepository serverRepository;
    private final TenantMongoAccess tenantMongoAccess;

    public Map<String, Object> getDashboard() {
        Date oneDayAgo = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date oneWeekAgo = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));

        long totalServers = serverRepository.count(new Query());
        long activeServers = serverRepository.count(Query.query(new Criteria().andOperator(
                MongoQueries.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.COMPLETED),
                MongoQueries.where(ServerFields.EMAIL_VERIFIED).is(true)
        )));
        long pendingServers = serverRepository.count(
                Query.query(MongoQueries.where(ServerFields.PROVISIONING_STATUS)
                        .in(ProvisioningStatus.PENDING, ProvisioningStatus.IN_PROGRESS))
        );
        long failedServers = serverRepository.count(
                Query.query(MongoQueries.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.FAILED))
        );

        long criticalLogs24h = systemLogRepository.count(Query.query(new Criteria().andOperator(
                MongoQueries.where(SystemLogFields.LEVEL).is("critical"),
                MongoQueries.where(SystemLogFields.TIMESTAMP).gte(oneDayAgo)
        )));
        long errorLogs24h = systemLogRepository.count(Query.query(new Criteria().andOperator(
                MongoQueries.where(SystemLogFields.LEVEL).is("error"),
                MongoQueries.where(SystemLogFields.TIMESTAMP).gte(oneDayAgo)
        )));
        long warningLogs24h = systemLogRepository.count(Query.query(new Criteria().andOperator(
                MongoQueries.where(SystemLogFields.LEVEL).is("warning"),
                MongoQueries.where(SystemLogFields.TIMESTAMP).gte(oneDayAgo)
        )));
        long totalLogs24h = systemLogRepository.count(
                Query.query(MongoQueries.where(SystemLogFields.TIMESTAMP).gte(oneDayAgo))
        );

        long unresolvedCritical = systemLogRepository.count(Query.query(new Criteria().andOperator(
                MongoQueries.where(SystemLogFields.LEVEL).is("critical"),
                MongoQueries.where(SystemLogFields.RESOLVED).is(false)
        )));
        long unresolvedErrors = systemLogRepository.count(Query.query(new Criteria().andOperator(
                MongoQueries.where(SystemLogFields.LEVEL).is("error"),
                MongoQueries.where(SystemLogFields.RESOLVED).is(false)
        )));

        long recentServers = serverRepository.count(
                Query.query(MongoQueries.where(ServerFields.CREATED_AT).gte(oneWeekAgo))
        );

        int healthScore = calculateHealthScore(
                totalServers,
                activeServers,
                failedServers,
                criticalLogs24h,
                errorLogs24h,
                unresolvedCritical,
                unresolvedErrors
        );
        String healthStatus = healthScore >= 95 ? "excellent"
                : healthScore >= 85 ? "good"
                : healthScore >= 70 ? "fair"
                : "poor";

        return Map.of(
                "success", true,
                "data", Map.of(
                        "servers", Map.of(
                                "total", totalServers,
                                "active", activeServers,
                                "pending", pendingServers,
                                "failed", failedServers,
                                "recentRegistrations", recentServers
                        ),
                        "logs", Map.of(
                                "last24h", Map.of(
                                        "total", totalLogs24h,
                                        "critical", criticalLogs24h,
                                        "error", errorLogs24h,
                                        "warning", warningLogs24h
                                ),
                                "unresolved", Map.of(
                                        "critical", unresolvedCritical,
                                        "error", unresolvedErrors
                                )
                        ),
                        "systemHealth", Map.of("score", healthScore, "status", healthStatus),
                        "trends", getLogTrends(oneWeekAgo),
                        "lastUpdated", new Date()
                )
        );
    }

    public Map<String, Object> getLogs(
            int page,
            int limit,
            String level,
            String source,
            String serverId,
            String category,
            String resolved,
            String search,
            String startDate,
            String endDate,
            String sort,
            String order
    ) {
        int pageNum = Math.max(1, page);
        int limitNum = Math.min(100, Math.max(1, limit));
        int skip = (pageNum - 1) * limitNum;

        Query query = buildLogsQuery(level, source, serverId, category, resolved, search, startDate, endDate);
        query.with(Sort.by("desc".equalsIgnoreCase(order) ? Sort.Direction.DESC : Sort.Direction.ASC, resolveSortField(sort)));
        query.skip(skip).limit(limitNum);

        List<SystemLog> logs = systemLogRepository.find(query);
        long total = systemLogRepository.count(Query.of(query).skip(0).limit(0));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("level", level);
        filters.put("source", source);
        filters.put("serverId", serverId);
        filters.put("category", category);
        filters.put("resolved", resolved);
        filters.put("search", search);

        return Map.of(
                "success", true,
                "data", Map.of(
                        "logs", logs,
                        "pagination", Map.of(
                                "page", pageNum,
                                "limit", limitNum,
                                "total", total,
                                "pages", (int) Math.ceil((double) total / limitNum)
                        ),
                        "filters", filters
                )
        );
    }

    public SystemLog createLog(CreateSystemLogRequest request) {
        SystemLog logData = request.toSystemLog();
        logData.setTimestamp(new Date());
        return systemLogRepository.saveEntity(logData);
    }

    public Map<String, Object> getSources() {
        List<String> sources = tenantMongoAccess.global().findDistinct(
                new Query(),
                SystemLogFields.SOURCE.path(),
                SystemLogMongoRepository.COLLECTION_NAME,
                String.class
        );
        List<String> categories = tenantMongoAccess.global().findDistinct(
                new Query(),
                SystemLogFields.CATEGORY.path(),
                SystemLogMongoRepository.COLLECTION_NAME,
                String.class
        );

        sources.removeIf(Objects::isNull);
        categories.removeIf(Objects::isNull);

        return Map.of(
                "success", true,
                "data", Map.of(
                        "sources", sources,
                        "categories", categories
                )
        );
    }

    public Optional<SystemLog> resolveLog(String id, ResolveLogRequest request) {
        Query query = Query.query(MongoQueries.where(SystemLogFields.ID).is(id));
        Update update = new Update();
        MongoUpdates.set(update, SystemLogFields.RESOLVED, true);
        MongoUpdates.set(update, SystemLogFields.RESOLVED_BY, request.resolvedBy() != null ? request.resolvedBy() : "admin");
        MongoUpdates.set(update, SystemLogFields.RESOLVED_AT, new Date());

        UpdateResult result = systemLogRepository.updateFirst(query, update);
        if (result.getModifiedCount() == 0) {
            return Optional.empty();
        }

        return systemLogRepository.findById(id);
    }

    public Map<String, Object> getHealth() {
        List<Map<String, Object>> checks = new ArrayList<>();
        String overallStatus = "healthy";

        try {
            long start = System.currentTimeMillis();
            tenantMongoAccess.global().getDb().runCommand(new Document("ping", 1));
            long responseTime = System.currentTimeMillis() - start;
            checks.add(Map.of(
                    "name", "Database Connectivity",
                    "status", "healthy",
                    "message", "MongoDB connection is responsive.",
                    "responseTime", responseTime
            ));
        } catch (Exception exception) {
            checks.add(Map.of(
                    "name", "Database Connectivity",
                    "status", "critical",
                    "message", "Failed to ping MongoDB.",
                    "error", exception.getMessage()
            ));
            overallStatus = "critical";
        }

        long criticalCount = systemLogRepository.count(Query.query(new Criteria().andOperator(
                MongoQueries.where(SystemLogFields.LEVEL).is("critical"),
                MongoQueries.where(SystemLogFields.RESOLVED).is(false),
                MongoQueries.where(SystemLogFields.TIMESTAMP).gte(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)))
        )));
        String logStatus = criticalCount > 5 ? "critical" : criticalCount > 0 ? "degraded" : "healthy";
        checks.add(Map.of(
                "name", "Critical System Logs",
                "status", logStatus,
                "message", criticalCount + " unresolved critical log(s) in the last 24 hours.",
                "count", criticalCount
        ));
        if ("critical".equals(logStatus)) {
            overallStatus = "critical";
        } else if ("degraded".equals(logStatus) && !"critical".equals(overallStatus)) {
            overallStatus = "degraded";
        }

        long failedCount = serverRepository.count(
                Query.query(MongoQueries.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.FAILED))
        );
        String serverStatus = failedCount > 0 ? "degraded" : "healthy";
        checks.add(Map.of(
                "name", "Server Provisioning",
                "status", serverStatus,
                "message", failedCount + " server(s) failed to provision.",
                "count", failedCount
        ));
        if ("degraded".equals(serverStatus) && !"critical".equals(overallStatus)) {
            overallStatus = "degraded";
        }

        return Map.of(
                "success", true,
                "data", Map.of(
                        "status", overallStatus,
                        "checks", checks,
                        "timestamp", new Date()
                )
        );
    }

    public long deleteLogs(List<String> logIds) {
        DeleteResult result = systemLogRepository.remove(
                Query.query(MongoQueries.where(SystemLogFields.ID).in(logIds))
        );
        return result.getDeletedCount();
    }

    public String exportLogs(
            String level,
            String source,
            String category,
            String resolved,
            String search,
            String startDate,
            String endDate
    ) {
        Query query = buildLogsQuery(
                normalizeAllFilter(level),
                normalizeAllFilter(source),
                null,
                normalizeAllFilter(category),
                normalizeAllFilter(resolved),
                search,
                startDate,
                endDate
        );
        query.with(MongoQueries.sort(Sort.Direction.DESC, SystemLogFields.TIMESTAMP)).limit(10000);

        List<SystemLog> logs = systemLogRepository.find(query);
        StringBuilder csv = new StringBuilder("Timestamp,Level,Source,Category,Message,Resolved,Resolved By\n");
        for (SystemLog logEntry : logs) {
            csv.append("\"").append(logEntry.getTimestamp()).append("\",");
            csv.append("\"").append(logEntry.getLevel() != null ? logEntry.getLevel() : "").append("\",");
            csv.append("\"").append(logEntry.getSource() != null ? logEntry.getSource() : "").append("\",");
            csv.append("\"").append(logEntry.getCategory() != null ? logEntry.getCategory() : "").append("\",");
            csv.append("\"").append(logEntry.getMessage() != null ? logEntry.getMessage().replace("\"", "\"\"") : "").append("\",");
            csv.append("\"").append(logEntry.isResolved() ? "Yes" : "No").append("\",");
            csv.append("\"").append(logEntry.getResolvedBy() != null ? logEntry.getResolvedBy() : "").append("\"\n");
        }
        return csv.toString();
    }

    public long clearAllLogs() {
        DeleteResult result = systemLogRepository.remove(new Query());
        log.info("All system logs cleared by admin");
        return result.getDeletedCount();
    }

    private Query buildLogsQuery(
            String level,
            String source,
            String serverId,
            String category,
            String resolved,
            String search,
            String startDate,
            String endDate
    ) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (hasText(level)) {
            criteriaList.add(MongoQueries.where(SystemLogFields.LEVEL).is(level));
        }
        if (hasText(source)) {
            criteriaList.add(MongoQueries.where(SystemLogFields.SOURCE).is(source));
        }
        if (hasText(serverId)) {
            criteriaList.add(MongoQueries.where(SystemLogFields.SERVER_ID).is(serverId));
        }
        if (hasText(category)) {
            criteriaList.add(MongoQueries.where(SystemLogFields.CATEGORY).is(category));
        }
        if (resolved != null) {
            criteriaList.add(MongoQueries.where(SystemLogFields.RESOLVED).is("true".equals(resolved)));
        }
        if (hasText(search)) {
            criteriaList.add(MongoQueries.where(SystemLogFields.MESSAGE).regex(Pattern.quote(search), "i"));
        }

        if (startDate != null || endDate != null) {
            Criteria dateCriteria = MongoQueries.where(SystemLogFields.TIMESTAMP);
            if (startDate != null) {
                dateCriteria = dateCriteria.gte(new Date(Long.parseLong(startDate)));
            }
            if (endDate != null) {
                dateCriteria = dateCriteria.lte(new Date(Long.parseLong(endDate)));
            }
            criteriaList.add(dateCriteria);
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        return query;
    }

    private String resolveSortField(String sort) {
        if (sort == null || sort.isBlank()) {
            return SystemLogFields.TIMESTAMP.path();
        }

        return switch (sort) {
            case "level" -> SystemLogFields.LEVEL.path();
            case "source" -> SystemLogFields.SOURCE.path();
            case "category" -> SystemLogFields.CATEGORY.path();
            case "resolved" -> SystemLogFields.RESOLVED.path();
            case "timestamp" -> SystemLogFields.TIMESTAMP.path();
            default -> SystemLogFields.TIMESTAMP.path();
        };
    }

    private int calculateHealthScore(long total, long active, long failed, long critical, long errors, long unresolvedCritical, long unresolvedErrors) {
        int score = 100;
        if (total > 0) {
            score -= (int) ((failed / (double) total) * 30);
        }
        score -= (int) Math.min(critical * 5, 25);
        score -= (int) Math.min(errors, 20);
        score -= (int) (unresolvedCritical * 10);
        score -= (int) (unresolvedErrors * 3);
        return Math.max(0, score);
    }

    private List<Document> getLogTrends(Date startDate) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(MongoQueries.where(SystemLogFields.TIMESTAMP).gte(startDate)),
                Aggregation.project()
                        .and(DateOperators.DateToString.dateOf(SystemLogFields.TIMESTAMP.path()).toString("%Y-%m-%d")).as("date")
                        .and(SystemLogFields.LEVEL.path()).as("level"),
                Aggregation.group("date", "level").count().as("count"),
                Aggregation.group("_id.date")
                        .push(new Document("level", "$_id.level").append("count", "$count")).as("levels")
                        .sum("count").as("total"),
                Aggregation.sort(Sort.Direction.ASC, "_id")
        );

        return systemLogRepository.aggregate(aggregation, Document.class).getMappedResults();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeAllFilter(String value) {
        return "all".equalsIgnoreCase(value) ? null : value;
    }
}
