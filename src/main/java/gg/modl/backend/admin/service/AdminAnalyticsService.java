package gg.modl.backend.admin.service;

import gg.modl.backend.analytics.data.MetricSnapshot;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAnalyticsService {
    private final ServerMongoRepository serverRepository;
    private final TenantMongoAccess tenantMongoAccess;
    private final AdminServerService adminServerService;

    public Map<String, Object> getDashboard(String range) {
        int days = resolveRangeDays(range);
        Date startDate = Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
        Date previousStartDate = Date.from(Instant.now().minus(days * 2L, ChronoUnit.DAYS));

        long totalServers = serverRepository.count(new Query());
        int refreshLimit = totalServers <= 200 ? (int) Math.max(totalServers, 1) : 50;
        adminServerService.refreshUsageStatsForActiveServers(refreshLimit);

        long activeServers = serverRepository.count(Query.query(new Criteria().andOperator(
                MongoQueries.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.COMPLETED),
                MongoQueries.where(ServerFields.EMAIL_VERIFIED).is(true)
        )));

        Aggregation userTicketAggregation = Aggregation.newAggregation(
                Aggregation.group()
                        .sum(ServerFields.USER_COUNT.path()).as("totalUsers")
                        .sum(ServerFields.TICKET_COUNT.path()).as("totalTickets")
        );
        Document userTicketResult = serverRepository.aggregate(userTicketAggregation, Document.class).getUniqueMappedResult();
        long totalUsers = getLong(userTicketResult, "totalUsers");
        long totalTickets = getLong(userTicketResult, "totalTickets");

        long currentPeriodServers = serverRepository.count(
                Query.query(MongoQueries.where(ServerFields.CREATED_AT).gte(startDate))
        );
        long previousPeriodServers = serverRepository.count(Query.query(new Criteria().andOperator(
                MongoQueries.where(ServerFields.CREATED_AT).gte(previousStartDate),
                MongoQueries.where(ServerFields.CREATED_AT).lt(startDate)
        )));
        double serverGrowthRate = previousPeriodServers > 0
                ? ((currentPeriodServers - previousPeriodServers) / (double) previousPeriodServers) * 100
                : (currentPeriodServers > 0 ? 100 : 0);

        Aggregation planAggregation = Aggregation.newAggregation(
                Aggregation.group(ServerFields.PLAN.path()).count().as("value"),
                Aggregation.project().and("_id").as("name").and("value").as("value")
        );
        List<Document> planResults = serverRepository.aggregate(planAggregation, Document.class).getMappedResults();

        Aggregation statusAggregation = Aggregation.newAggregation(
                Aggregation.group(ServerFields.PROVISIONING_STATUS.path()).count().as("value"),
                Aggregation.project().and("_id").as("name").and("value").as("value")
        );
        List<Document> statusResults = serverRepository.aggregate(statusAggregation, Document.class).getMappedResults();

        Aggregation registrationTrendAggregation = Aggregation.newAggregation(
                Aggregation.match(MongoQueries.where(ServerFields.CREATED_AT).gte(startDate)),
                Aggregation.project()
                        .and(DateOperators.DateToString.dateOf(ServerFields.CREATED_AT.path()).toString("%Y-%m-%d")).as("date"),
                Aggregation.group("date").count().as("servers"),
                Aggregation.sort(Sort.Direction.ASC, "_id"),
                Aggregation.project().and("_id").as("date").and("servers").as("servers")
        );
        List<Document> registrationTrend = serverRepository.aggregate(registrationTrendAggregation, Document.class).getMappedResults();

        Query topServersQuery = Query.query(new Criteria().andOperator(
                MongoQueries.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.COMPLETED),
                MongoQueries.where(ServerFields.EMAIL_VERIFIED).is(true),
                MongoQueries.where(ServerFields.USER_COUNT).gt(0)
        ));
        topServersQuery.with(MongoQueries.sort(Sort.Direction.DESC, ServerFields.USER_COUNT));
        topServersQuery.limit(10);
        List<Server> topServers = serverRepository.find(topServersQuery);

        long serversWithData = serverRepository.count(Query.query(new Criteria().andOperator(
                MongoQueries.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.COMPLETED),
                MongoQueries.where(ServerFields.USER_COUNT).gt(0)
        )));
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
                        "registrationTrend", registrationTrend
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
        return response;
    }

    public Map<String, Object> getActivity(String range) {
        int days = resolveRangeDays(range);
        Date startDate = Date.from(Instant.now().minus(days, ChronoUnit.DAYS));

        List<MetricSnapshot> snapshots = tenantMongoAccess.global().find(
                Query.query(Criteria.where("date").gte(startDate)).with(Sort.by(Sort.Direction.ASC, "date")),
                MetricSnapshot.class,
                CollectionName.METRIC_SNAPSHOTS
        );

        List<Map<String, Object>> data = snapshots.stream().map(s -> Map.<String, Object>of(
                "date", s.getDate().toInstant().toString(),
                "activeServers", s.getActiveServers(),
                "totalPlayers", s.getTotalPlayers(),
                "onlinePlayers", s.getOnlinePlayers(),
                "totalServers", s.getTotalServers()
        )).toList();

        return Map.of("success", true, "data", data);
    }

    public Map<String, Object> getUsage() {
        Date thirtyDaysAgo = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));

        long activeServers = serverRepository.count(
                Query.query(MongoQueries.where(ServerFields.LAST_ACTIVITY_AT).gte(thirtyDaysAgo))
        );
        long totalServers = serverRepository.count(new Query());
        Document dbStats = tenantMongoAccess.global().getDb().runCommand(new Document("dbStats", 1));
        long storageSize = getLong(dbStats, "storageSize");

        return Map.of(
                "success", true,
                "data", Map.of(
                        "userEngagement", Map.of("monthlyActiveServers", activeServers),
                        "resourceUtilization", Map.of(
                                "storage", storageSize,
                                "storagePercent", totalServers > 0 ? (storageSize / (totalServers * 104857600.0)) * 100 : 0,
                                "apiCalls", 0,
                                "databaseQueries", 0
                        )
                )
        );
    }

    public Map<String, Object> getHistorical(String metric, String range) {
        if (metric == null || (!metric.equals("servers") && !metric.equals("users") && !metric.equals("tickets"))) {
            return Map.of("success", false, "error", "Invalid metric type");
        }

        int days = resolveRangeDays(range);
        Date startDate = Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
        Aggregation aggregation = buildHistoricalAggregation(metric, startDate);
        List<Document> results = serverRepository.aggregate(aggregation, Document.class).getMappedResults();

        return Map.of(
                "success", true,
                "data", Map.of(
                        "metric", metric,
                        "range", normalizeRange(range),
                        "data", results
                )
        );
    }

    private Aggregation buildHistoricalAggregation(String metric, Date startDate) {
        var projectDateStage = Aggregation.project()
                .and(DateOperators.DateToString.dateOf(ServerFields.CREATED_AT.path()).toString("%Y-%m-%d")).as("date");

        if ("users".equals(metric) || "tickets".equals(metric)) {
            String sumField = "users".equals(metric) ? ServerFields.USER_COUNT.path() : ServerFields.TICKET_COUNT.path();
            return Aggregation.newAggregation(
                    Aggregation.match(MongoQueries.where(ServerFields.CREATED_AT).gte(startDate)),
                    projectDateStage.and(sumField).as("valueSource"),
                    Aggregation.group("date").sum("valueSource").as("value"),
                    Aggregation.sort(Sort.Direction.ASC, "_id"),
                    Aggregation.project().and("_id").as("date").and("value").as("value")
            );
        }

        return Aggregation.newAggregation(
                Aggregation.match(MongoQueries.where(ServerFields.CREATED_AT).gte(startDate)),
                projectDateStage,
                Aggregation.group("date").count().as("value"),
                Aggregation.sort(Sort.Direction.ASC, "_id"),
                Aggregation.project().and("_id").as("date").and("value").as("value")
        );
    }

    private int resolveRangeDays(String range) {
        return switch (normalizeRange(range)) {
            case "7d" -> 7;
            case "90d" -> 90;
            case "365d", "1y" -> 365;
            default -> 30;
        };
    }

    private String normalizeRange(String range) {
        return range == null || range.isBlank() ? "30d" : range;
    }

    private long getLong(Document document, String fieldName) {
        if (document == null) {
            return 0L;
        }

        Object value = document.get(fieldName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
