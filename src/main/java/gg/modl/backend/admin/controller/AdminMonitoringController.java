package gg.modl.backend.admin.controller;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.admin.data.SystemLog;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_MONITORING)
@RequiredArgsConstructor
@Slf4j
public class AdminMonitoringController {
    private static final String LOGS_COLLECTION = "system_logs";

    private final DynamicMongoTemplateProvider mongoProvider;

    private MongoTemplate getTemplate() {
        return mongoProvider.getGlobalDatabase();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        try {
            Date oneDayAgo = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
            Date oneWeekAgo = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));

            // Server counts
            long totalServers = getTemplate().count(new Query(), Server.class, CollectionName.MODL_SERVERS);
            long activeServers = getTemplate().count(
                    Query.query(Criteria.where("provisioningStatus").is("completed").and("emailVerified").is(true)),
                    Server.class, CollectionName.MODL_SERVERS
            );
            long pendingServers = getTemplate().count(
                    Query.query(Criteria.where("provisioningStatus").in("pending", "in-progress")),
                    Server.class, CollectionName.MODL_SERVERS
            );
            long failedServers = getTemplate().count(
                    Query.query(Criteria.where("provisioningStatus").is("failed")),
                    Server.class, CollectionName.MODL_SERVERS
            );

            // Log counts
            long criticalLogs24h = getTemplate().count(
                    Query.query(Criteria.where("level").is("critical").and("timestamp").gte(oneDayAgo)),
                    SystemLog.class, LOGS_COLLECTION
            );
            long errorLogs24h = getTemplate().count(
                    Query.query(Criteria.where("level").is("error").and("timestamp").gte(oneDayAgo)),
                    SystemLog.class, LOGS_COLLECTION
            );
            long warningLogs24h = getTemplate().count(
                    Query.query(Criteria.where("level").is("warning").and("timestamp").gte(oneDayAgo)),
                    SystemLog.class, LOGS_COLLECTION
            );
            long totalLogs24h = getTemplate().count(
                    Query.query(Criteria.where("timestamp").gte(oneDayAgo)),
                    SystemLog.class, LOGS_COLLECTION
            );

            long unresolvedCritical = getTemplate().count(
                    Query.query(Criteria.where("level").is("critical").and("resolved").is(false)),
                    SystemLog.class, LOGS_COLLECTION
            );
            long unresolvedErrors = getTemplate().count(
                    Query.query(Criteria.where("level").is("error").and("resolved").is(false)),
                    SystemLog.class, LOGS_COLLECTION
            );

            long recentServers = getTemplate().count(
                    Query.query(Criteria.where("createdAt").gte(oneWeekAgo)),
                    Server.class, CollectionName.MODL_SERVERS
            );

            int healthScore = calculateHealthScore(totalServers, activeServers, failedServers, criticalLogs24h, errorLogs24h, unresolvedCritical, unresolvedErrors);
            String healthStatus = healthScore >= 95 ? "excellent" : healthScore >= 85 ? "good" : healthScore >= 70 ? "fair" : "poor";

            // Log trends
            List<Document> trends = getLogTrends(oneWeekAgo);

            return ResponseEntity.ok(Map.of(
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
                                    "last24h", Map.of("total", totalLogs24h, "critical", criticalLogs24h, "error", errorLogs24h, "warning", warningLogs24h),
                                    "unresolved", Map.of("critical", unresolvedCritical, "error", unresolvedErrors)
                            ),
                            "systemHealth", Map.of("score", healthScore, "status", healthStatus),
                            "trends", trends,
                            "lastUpdated", new Date()
                    )
            ));
        } catch (Exception e) {
            log.error("Dashboard metrics error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch dashboard metrics"));
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String serverId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String resolved,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "timestamp") String sort,
            @RequestParam(defaultValue = "desc") String order) {

        int pageNum = Math.max(1, page);
        int limitNum = Math.min(100, Math.max(1, limit));
        int skip = (pageNum - 1) * limitNum;

        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (level != null && !level.isEmpty()) criteriaList.add(Criteria.where("level").is(level));
        if (source != null && !source.isEmpty()) criteriaList.add(Criteria.where("source").is(source));
        if (serverId != null && !serverId.isEmpty()) criteriaList.add(Criteria.where("serverId").is(serverId));
        if (category != null && !category.isEmpty()) criteriaList.add(Criteria.where("category").is(category));
        if (resolved != null) criteriaList.add(Criteria.where("resolved").is("true".equals(resolved)));
        if (search != null && !search.isEmpty()) criteriaList.add(Criteria.where("message").regex(Pattern.quote(search), "i"));

        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("timestamp");
            if (startDate != null) dateCriteria = dateCriteria.gte(new Date(Long.parseLong(startDate)));
            if (endDate != null) dateCriteria = dateCriteria.lte(new Date(Long.parseLong(endDate)));
            criteriaList.add(dateCriteria);
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        query.with(Sort.by("desc".equals(order) ? Sort.Direction.DESC : Sort.Direction.ASC, sort));
        query.skip(skip).limit(limitNum);

        List<SystemLog> logs = getTemplate().find(query, SystemLog.class, LOGS_COLLECTION);
        long total = getTemplate().count(Query.of(query).skip(0).limit(0), SystemLog.class, LOGS_COLLECTION);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "logs", logs,
                        "pagination", Map.of("page", pageNum, "limit", limitNum, "total", total, "pages", (int) Math.ceil((double) total / limitNum)),
                        "filters", Map.of("level", level, "source", source, "serverId", serverId, "category", category, "resolved", resolved, "search", search)
                )
        ));
    }

    @PostMapping("/logs")
    public ResponseEntity<?> createLog(@RequestBody SystemLog logData) {
        if (logData.getLevel() == null || logData.getMessage() == null || logData.getSource() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing required fields: level, message, source"));
        }

        logData.setTimestamp(new Date());
        SystemLog saved = getTemplate().save(logData, LOGS_COLLECTION);

        return ResponseEntity.status(201).body(Map.of("success", true, "data", saved, "message", "Log entry created successfully"));
    }

    @GetMapping("/sources")
    public ResponseEntity<?> getSources() {
        try {
            List<String> sources = getTemplate().findDistinct(new Query(), "source", LOGS_COLLECTION, String.class);
            List<String> categories = getTemplate().findDistinct(new Query(), "category", LOGS_COLLECTION, String.class);

            sources.removeIf(Objects::isNull);
            categories.removeIf(Objects::isNull);

            return ResponseEntity.ok(Map.of("success", true, "data", Map.of("sources", sources, "categories", categories)));
        } catch (Exception e) {
            log.error("Get sources error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch sources"));
        }
    }

    @PutMapping("/logs/{id}/resolve")
    public ResponseEntity<?> resolveLog(@PathVariable String id, @RequestBody Map<String, String> request) {
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("resolved", true)
                .set("resolvedBy", request.getOrDefault("resolvedBy", "admin"))
                .set("resolvedAt", new Date());

        UpdateResult result = getTemplate().updateFirst(query, update, SystemLog.class, LOGS_COLLECTION);
        if (result.getModifiedCount() == 0) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Log entry not found"));
        }

        SystemLog updated = getTemplate().findById(id, SystemLog.class, LOGS_COLLECTION);
        return ResponseEntity.ok(Map.of("success", true, "data", updated, "message", "Log entry marked as resolved"));
    }

    @GetMapping("/health")
    public ResponseEntity<?> getHealth() {
        try {
            List<Map<String, Object>> checks = new ArrayList<>();
            String overallStatus = "healthy";

            // Database check
            try {
                long start = System.currentTimeMillis();
                getTemplate().getDb().runCommand(new Document("ping", 1));
                long responseTime = System.currentTimeMillis() - start;
                checks.add(Map.of("name", "Database Connectivity", "status", "healthy", "message", "MongoDB connection is responsive.", "responseTime", responseTime));
            } catch (Exception e) {
                checks.add(Map.of("name", "Database Connectivity", "status", "critical", "message", "Failed to ping MongoDB.", "error", e.getMessage()));
                overallStatus = "critical";
            }

            // Critical logs check
            long criticalCount = getTemplate().count(
                    Query.query(Criteria.where("level").is("critical").and("resolved").is(false).and("timestamp").gte(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)))),
                    SystemLog.class, LOGS_COLLECTION
            );
            String logStatus = criticalCount > 5 ? "critical" : criticalCount > 0 ? "degraded" : "healthy";
            checks.add(Map.of("name", "Critical System Logs", "status", logStatus, "message", criticalCount + " unresolved critical log(s) in the last 24 hours.", "count", criticalCount));
            if ("critical".equals(logStatus)) overallStatus = "critical";
            else if ("degraded".equals(logStatus) && !"critical".equals(overallStatus)) overallStatus = "degraded";

            // Failed servers check
            long failedCount = getTemplate().count(
                    Query.query(Criteria.where("provisioningStatus").is("failed")),
                    Server.class, CollectionName.MODL_SERVERS
            );
            String serverStatus = failedCount > 0 ? "degraded" : "healthy";
            checks.add(Map.of("name", "Server Provisioning", "status", serverStatus, "message", failedCount + " server(s) failed to provision.", "count", failedCount));
            if ("degraded".equals(serverStatus) && !"critical".equals(overallStatus)) overallStatus = "degraded";

            return ResponseEntity.ok(Map.of("success", true, "data", Map.of("status", overallStatus, "checks", checks, "timestamp", new Date())));
        } catch (Exception e) {
            log.error("Health check error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Health check failed"));
        }
    }

    @PostMapping("/logs/delete")
    public ResponseEntity<?> deleteLogs(@RequestBody Map<String, List<String>> request) {
        List<String> logIds = request.get("logIds");
        if (logIds == null || logIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Log IDs are required"));
        }

        DeleteResult result = getTemplate().remove(Query.query(Criteria.where("_id").in(logIds)), SystemLog.class, LOGS_COLLECTION);
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("deletedCount", result.getDeletedCount()), "message", "Successfully deleted " + result.getDeletedCount() + " log(s)"));
    }

    @GetMapping("/logs/export")
    public ResponseEntity<?> exportLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String resolved,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (level != null && !"all".equals(level)) criteriaList.add(Criteria.where("level").is(level));
        if (source != null && !"all".equals(source)) criteriaList.add(Criteria.where("source").is(source));
        if (category != null && !"all".equals(category)) criteriaList.add(Criteria.where("category").is(category));
        if (resolved != null && !"all".equals(resolved)) criteriaList.add(Criteria.where("resolved").is("true".equals(resolved)));
        if (search != null) criteriaList.add(Criteria.where("message").regex(search, "i"));

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        query.with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(10000);
        List<SystemLog> logs = getTemplate().find(query, SystemLog.class, LOGS_COLLECTION);

        StringBuilder csv = new StringBuilder("Timestamp,Level,Source,Category,Message,Resolved,Resolved By\n");
        for (SystemLog l : logs) {
            csv.append("\"").append(l.getTimestamp()).append("\",");
            csv.append("\"").append(l.getLevel() != null ? l.getLevel() : "").append("\",");
            csv.append("\"").append(l.getSource() != null ? l.getSource() : "").append("\",");
            csv.append("\"").append(l.getCategory() != null ? l.getCategory() : "").append("\",");
            csv.append("\"").append(l.getMessage() != null ? l.getMessage().replace("\"", "\"\"") : "").append("\",");
            csv.append("\"").append(l.isResolved() ? "Yes" : "No").append("\",");
            csv.append("\"").append(l.getResolvedBy() != null ? l.getResolvedBy() : "").append("\"\n");
        }

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"system-logs-" + new Date().toInstant().toString().split("T")[0] + ".csv\"")
                .body(csv.toString());
    }

    @PostMapping("/logs/clear-all")
    public ResponseEntity<?> clearAllLogs() {
        DeleteResult result = getTemplate().remove(new Query(), SystemLog.class, LOGS_COLLECTION);
        log.info("All system logs cleared by admin");
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("deletedCount", result.getDeletedCount()), "message", "Successfully cleared all logs"));
    }

    @GetMapping("/pm2-status")
    public ResponseEntity<?> getPm2Status() {
        // PM2 integration placeholder - would require native process monitoring
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "isEnabled", false,
                        "isStreaming", false,
                        "reconnectAttempts", 0,
                        "recentLogsCount", 0,
                        "lastLogTime", null
                )
        ));
    }

    @PostMapping("/pm2/restart")
    public ResponseEntity<?> restartPm2() {
        return ResponseEntity.ok(Map.of("success", true, "message", "PM2 log streaming restarted"));
    }

    @PostMapping("/pm2/toggle")
    public ResponseEntity<?> togglePm2(@RequestBody Map<String, Boolean> request) {
        boolean enabled = request.getOrDefault("enabled", false);
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("isEnabled", enabled, "isStreaming", enabled), "message", "PM2 log streaming " + (enabled ? "enabled" : "disabled")));
    }

    private int calculateHealthScore(long total, long active, long failed, long critical, long errors, long unresolvedCritical, long unresolvedErrors) {
        int score = 100;
        if (total > 0) score -= (int) ((failed / (double) total) * 30);
        score -= (int) Math.min(critical * 5, 25);
        score -= (int) Math.min(errors, 20);
        score -= (int) (unresolvedCritical * 10);
        score -= (int) (unresolvedErrors * 3);
        return Math.max(0, score);
    }

    private List<Document> getLogTrends(Date startDate) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("timestamp").gte(startDate)),
                Aggregation.project()
                        .and(DateOperators.DateToString.dateOf("timestamp").toString("%Y-%m-%d")).as("date")
                        .and("level").as("level"),
                Aggregation.group("date", "level").count().as("count"),
                Aggregation.group("_id.date")
                        .push(new Document("level", "$_id.level").append("count", "$count")).as("levels")
                        .sum("count").as("total"),
                Aggregation.sort(Sort.Direction.ASC, "_id")
        );

        return getTemplate().aggregate(agg, LOGS_COLLECTION, Document.class).getMappedResults();
    }
}
