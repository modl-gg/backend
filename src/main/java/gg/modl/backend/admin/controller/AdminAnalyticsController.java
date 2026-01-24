package gg.modl.backend.admin.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_ANALYTICS)
@RequiredArgsConstructor
@Slf4j
public class AdminAnalyticsController {
    private final DynamicMongoTemplateProvider mongoProvider;

    private MongoTemplate getTemplate() {
        return mongoProvider.getGlobalDatabase();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@RequestParam(defaultValue = "30d") String range) {
        try {
            int days = switch (range) {
                case "7d" -> 7;
                case "90d" -> 90;
                case "365d" -> 365;
                default -> 30;
            };

            Date startDate = Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
            Date previousStartDate = Date.from(Instant.now().minus(days * 2L, ChronoUnit.DAYS));

            // Overview metrics
            long totalServers = getTemplate().count(new Query(), Server.class, CollectionName.MODL_SERVERS);
            long activeServers = getTemplate().count(
                    Query.query(Criteria.where("updatedAt").gte(Date.from(Instant.now().minus(30, ChronoUnit.DAYS)))),
                    Server.class, CollectionName.MODL_SERVERS
            );

            // User and ticket counts aggregation
            Aggregation userTicketAgg = Aggregation.newAggregation(
                    Aggregation.group().sum("userCount").as("totalUsers").sum("ticketCount").as("totalTickets")
            );
            Document userTicketResult = getTemplate().aggregate(userTicketAgg, CollectionName.MODL_SERVERS, Document.class).getUniqueMappedResult();
            long totalUsers = userTicketResult != null ? userTicketResult.getInteger("totalUsers", 0) : 0;
            long totalTickets = userTicketResult != null ? userTicketResult.getInteger("totalTickets", 0) : 0;

            // Growth rates
            long currentPeriodServers = getTemplate().count(Query.query(Criteria.where("createdAt").gte(startDate)), Server.class, CollectionName.MODL_SERVERS);
            long previousPeriodServers = getTemplate().count(
                    Query.query(Criteria.where("createdAt").gte(previousStartDate).lt(startDate)),
                    Server.class, CollectionName.MODL_SERVERS
            );
            double serverGrowthRate = previousPeriodServers > 0 ? ((currentPeriodServers - previousPeriodServers) / (double) previousPeriodServers) * 100 : (currentPeriodServers > 0 ? 100 : 0);

            // Plan distribution
            Aggregation planAgg = Aggregation.newAggregation(
                    Aggregation.group("plan").count().as("value"),
                    Aggregation.project().and("_id").as("name").and("value").as("value")
            );
            List<Document> planResults = getTemplate().aggregate(planAgg, CollectionName.MODL_SERVERS, Document.class).getMappedResults();

            // Status distribution
            Aggregation statusAgg = Aggregation.newAggregation(
                    Aggregation.group("provisioningStatus").count().as("value"),
                    Aggregation.project().and("_id").as("name").and("value").as("value")
            );
            List<Document> statusResults = getTemplate().aggregate(statusAgg, CollectionName.MODL_SERVERS, Document.class).getMappedResults();

            // Registration trend
            Aggregation regTrendAgg = Aggregation.newAggregation(
                    Aggregation.match(Criteria.where("createdAt").gte(startDate)),
                    Aggregation.project().and(DateOperators.DateToString.dateOf("createdAt").toString("%Y-%m-%d")).as("date"),
                    Aggregation.group("date").count().as("servers"),
                    Aggregation.sort(Sort.Direction.ASC, "_id"),
                    Aggregation.project().and("_id").as("date").and("servers").as("servers")
            );
            List<Document> regTrendResults = getTemplate().aggregate(regTrendAgg, CollectionName.MODL_SERVERS, Document.class).getMappedResults();

            // Top servers by users
            Query topServersQuery = new Query(Criteria.where("userCount").gt(0));
            topServersQuery.with(Sort.by(Sort.Direction.DESC, "userCount"));
            topServersQuery.limit(10);
            List<Server> topServers = getTemplate().find(topServersQuery, Server.class, CollectionName.MODL_SERVERS);

            // Calculate averages
            long serversWithData = getTemplate().count(
                    Query.query(Criteria.where("provisioningStatus").is("completed").and("userCount").gt(0)),
                    Server.class, CollectionName.MODL_SERVERS
            );
            double avgPlayersPerServer = serversWithData > 0 ? (double) totalUsers / serversWithData : 0;
            double avgTicketsPerServer = serversWithData > 0 ? (double) totalTickets / serversWithData : 0;

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", Map.of(
                    "overview", Map.of(
                            "totalServers", totalServers,
                            "activeServers", activeServers,
                            "totalUsers", totalUsers,
                            "totalTickets", totalTickets,
                            "serverGrowthRate", String.format("%.2f", serverGrowthRate),
                            "userGrowthRate", "0.00",
                            "avgPlayersPerServer", String.format("%.1f", avgPlayersPerServer),
                            "avgTicketsPerServer", String.format("%.1f", avgTicketsPerServer)
                    ),
                    "serverMetrics", Map.of(
                            "byPlan", planResults,
                            "byStatus", statusResults,
                            "registrationTrend", regTrendResults
                    ),
                    "usageStatistics", Map.of(
                            "topServersByUsers", topServers,
                            "serverActivity", Collections.emptyList(),
                            "geographicDistribution", Collections.emptyList(),
                            "playerGrowth", Collections.emptyList(),
                            "ticketVolume", Collections.emptyList()
                    ),
                    "systemHealth", Map.of("errorRates", Collections.emptyList())
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Analytics dashboard error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch analytics data"));
        }
    }

    @GetMapping("/usage")
    public ResponseEntity<?> getUsage() {
        try {
            Date thirtyDaysAgo = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));

            long activeServers = getTemplate().count(
                    Query.query(Criteria.where("lastActivityAt").gte(thirtyDaysAgo)),
                    Server.class, CollectionName.MODL_SERVERS
            );
            long totalServers = getTemplate().count(new Query(), Server.class, CollectionName.MODL_SERVERS);

            Document dbStats = getTemplate().getDb().runCommand(new Document("dbStats", 1));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "userEngagement", Map.of("monthlyActiveServers", activeServers),
                            "resourceUtilization", Map.of(
                                    "storage", dbStats.get("storageSize", 0L),
                                    "storagePercent", totalServers > 0 ? (dbStats.getLong("storageSize") / (totalServers * 104857600.0)) * 100 : 0,
                                    "apiCalls", 0,
                                    "databaseQueries", 0
                            )
                    )
            ));
        } catch (Exception e) {
            log.error("Usage statistics error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch usage statistics"));
        }
    }

    @GetMapping("/historical")
    public ResponseEntity<?> getHistorical(
            @RequestParam(required = false) String metric,
            @RequestParam(defaultValue = "30d") String range) {
        try {
            int days = switch (range) {
                case "7d" -> 7;
                case "90d" -> 90;
                case "365d" -> 365;
                default -> 30;
            };

            Date startDate = Date.from(Instant.now().minus(days, ChronoUnit.DAYS));

            if (metric == null || (!metric.equals("servers") && !metric.equals("users") && !metric.equals("tickets"))) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid metric type"));
            }

            String sumField = switch (metric) {
                case "users" -> "userCount";
                case "tickets" -> "ticketCount";
                default -> null;
            };

            Aggregation agg;
            if (sumField != null) {
                agg = Aggregation.newAggregation(
                        Aggregation.match(Criteria.where("createdAt").gte(startDate)),
                        Aggregation.project().and(DateOperators.DateToString.dateOf("createdAt").toString("%Y-%m-%d")).as("date").and(sumField).as("val"),
                        Aggregation.group("date").sum("val").as("value"),
                        Aggregation.sort(Sort.Direction.ASC, "_id"),
                        Aggregation.project().and("_id").as("date").and("value").as("value")
                );
            } else {
                agg = Aggregation.newAggregation(
                        Aggregation.match(Criteria.where("createdAt").gte(startDate)),
                        Aggregation.project().and(DateOperators.DateToString.dateOf("createdAt").toString("%Y-%m-%d")).as("date"),
                        Aggregation.group("date").count().as("value"),
                        Aggregation.sort(Sort.Direction.ASC, "_id"),
                        Aggregation.project().and("_id").as("date").and("value").as("value")
                );
            }

            List<Document> results = getTemplate().aggregate(agg, CollectionName.MODL_SERVERS, Document.class).getMappedResults();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of("metric", metric, "range", range, "data", results)
            ));
        } catch (Exception e) {
            log.error("Historical data error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch historical data"));
        }
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportAnalytics(@RequestBody Map<String, String> request) {
        String type = request.getOrDefault("type", "json");
        String range = request.getOrDefault("range", "30d");

        if ("csv".equals(type)) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=\"modl-analytics-" + range + ".csv\"")
                    .body("Date,Servers,Users,Tickets\n2024-01-01,100,1500,820");
        } else if ("json".equals(type)) {
            return ResponseEntity.ok(Map.of(
                    "exportDate", new Date().toString(),
                    "range", range,
                    "data", Map.of("servers", 100, "users", 1500, "tickets", 820)
            ));
        }

        return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid export type"));
    }

    @PostMapping("/report")
    public ResponseEntity<?> generateReport(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(501).body(Map.of("success", false, "error", "Report generation not implemented"));
    }
}
