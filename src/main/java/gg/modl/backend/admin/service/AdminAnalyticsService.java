package gg.modl.backend.admin.service;

import gg.modl.backend.analytics.data.MetricSnapshot;
import gg.modl.backend.database.mongo.repository.GlobalMongoAdminRepository;
import gg.modl.backend.database.mongo.repository.MetricSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.server.data.Server;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository.DateServersResult;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository.DateValueResult;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository.NameValueResult;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAnalyticsService {
    private final ServerMongoRepository serverRepository;
    private final MetricSnapshotMongoRepository metricSnapshotRepository;
    private final GlobalMongoAdminRepository globalMongoAdminRepository;
    private final AdminServerService adminServerService;

    public Map<String, Object> getDashboard(String range) {
        int days = resolveRangeDays(range);
        Date startDate = Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
        Date previousStartDate = Date.from(Instant.now().minus(days * 2L, ChronoUnit.DAYS));

        long totalServers = serverRepository.countAll();
        int refreshLimit = totalServers <= 200 ? (int) Math.max(totalServers, 1) : 50;
        adminServerService.refreshUsageStatsForActiveServers(refreshLimit);

        long activeServers = serverRepository.countCompletedAndVerified();
        ServerMongoRepository.UsageTotals usageTotals = serverRepository.getUsageTotals();
        long totalUsers = usageTotals.totalUsers();
        long totalTickets = usageTotals.totalTickets();

        long currentPeriodServers = serverRepository.countCreatedSince(startDate);
        long previousPeriodServers = serverRepository.countCreatedBetween(previousStartDate, startDate);
        double serverGrowthRate = previousPeriodServers > 0
                                  ? ((currentPeriodServers - previousPeriodServers) / (double) previousPeriodServers) * 100
                                  : (currentPeriodServers > 0 ? 100 : 0);

        List<NameValueResult> planResults = serverRepository.aggregatePlanCounts();
        List<NameValueResult> statusResults = serverRepository.aggregateProvisioningStatusCounts();
        List<DateServersResult> registrationTrend = serverRepository.findRegistrationTrend(startDate);
        List<Server> topServers = serverRepository.findTopCompletedVerifiedByUserCount(10);

        long serversWithData = serverRepository.countCompletedWithUsers();
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

    public Map<String, Object> getActivity(String range) {
        int days = resolveRangeDays(range);
        Date startDate = Date.from(Instant.now().minus(days, ChronoUnit.DAYS));

        List<MetricSnapshot> snapshots = metricSnapshotRepository.findSinceOrdered(startDate);

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

        long activeServers = serverRepository.countActiveSince(thirtyDaysAgo);
        long totalServers = serverRepository.countAll();
        long storageSize = globalMongoAdminRepository.getStorageSize();

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
        List<DateValueResult> results = serverRepository.aggregateHistoricalMetric(metric, startDate);

        return Map.of(
            "success", true,
            "data", Map.of(
                "metric", metric,
                "range", normalizeRange(range),
                "data", results
            )
        );
    }

    public Object exportAnalytics(String type, String range) {
        String normalizedType = type != null ? type : "json";
        String normalizedRange = normalizeRange(range);

        if ("csv".equals(normalizedType)) {
            return "Date,Servers,Users,Tickets\n2024-01-01,100,1500,820";
        }

        return Map.of(
            "exportDate", new java.util.Date().toString(),
            "range", normalizedRange,
            "data", Map.of("servers", 100, "users", 1500, "tickets", 820)
        );
    }
}
