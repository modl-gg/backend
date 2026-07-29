package gg.modl.backend.admin.service;

import gg.modl.backend.admin.dto.response.AdminAnalyticsActivity;
import gg.modl.backend.admin.dto.response.AdminAnalyticsDashboard;
import gg.modl.backend.admin.dto.response.AdminAnalyticsExport;
import gg.modl.backend.admin.dto.response.AdminAnalyticsHistorical;
import gg.modl.backend.admin.dto.response.AdminAnalyticsUsage;
import gg.modl.backend.admin.dto.response.AdminHistoricalPoint;
import gg.modl.backend.admin.dto.response.AdminNameCount;
import gg.modl.backend.admin.dto.response.AdminRegistrationPoint;
import gg.modl.backend.analytics.data.MetricSnapshot;
import gg.modl.backend.analytics.data.ServerInstanceSnapshot;
import gg.modl.backend.database.mongo.repository.GlobalMongoAdminRepository;
import gg.modl.backend.database.mongo.repository.MetricSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerInstanceSnapshotMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMetricsRepository;
import gg.modl.backend.database.mongo.repository.ServerMetricsRepository.DateServersResult;
import gg.modl.backend.database.mongo.repository.ServerMetricsRepository.DateValueResult;
import gg.modl.backend.database.mongo.repository.ServerMetricsRepository.NameValueResult;
import gg.modl.backend.database.mongo.repository.ServerUsageRepository;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.util.DateRangeUtil;
import gg.modl.backend.server.data.Server;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAnalyticsService {
    private final ServerMetricsRepository serverMetricsRepository;
    private final ServerUsageRepository serverUsageRepository;
    private final MetricSnapshotMongoRepository metricSnapshotRepository;
    private final ServerInstanceSnapshotMongoRepository serverInstanceSnapshotRepository;
    private final GlobalMongoAdminRepository globalMongoAdminRepository;
    private final AdminServerService adminServerService;

    public AdminAnalyticsDashboard getDashboard(String range) {
        int days = DateRangeUtil.resolveRangeDays(range);
        Instant now = Instant.now();
        Date startDate = Date.from(now.minus(days, ChronoUnit.DAYS));
        Date previousStartDate = Date.from(now.minus(days * 2L, ChronoUnit.DAYS));

        ServerMetricsRepository.DashboardStats stats = serverMetricsRepository.aggregateDashboardStats(startDate, previousStartDate);

        long totalServers = stats.totalServers();
        int refreshLimit = totalServers <= 200 ? (int) Math.max(totalServers, 1) : 50;
        adminServerService.refreshUsageStatsForActiveServers(refreshLimit);

        long activeServers = stats.activeServers();
        long totalUsers = stats.totalUsers();
        long totalTickets = stats.totalTickets();

        long currentPeriodServers = stats.currentPeriodServers();
        long previousPeriodServers = stats.previousPeriodServers();
        double serverGrowthRate = previousPeriodServers > 0
                                  ? ((currentPeriodServers - previousPeriodServers) / (double) previousPeriodServers) * 100
                                  : (currentPeriodServers > 0 ? 100 : 0);

        List<NameValueResult> planResults = serverMetricsRepository.aggregatePlanCounts();
        List<NameValueResult> statusResults = serverMetricsRepository.aggregateProvisioningStatusCounts();
        List<DateServersResult> registrationTrend = serverMetricsRepository.findRegistrationTrend(startDate);
        List<Server> topServers = serverMetricsRepository.findTopCompletedVerifiedByUserCount(10);

        long serversWithData = stats.serversWithData();
        double avgPlayersPerServer = serversWithData > 0 ? (double) totalUsers / serversWithData : 0;
        double avgTicketsPerServer = serversWithData > 0 ? (double) totalTickets / serversWithData : 0;

        Date snapshotCutoff = Date.from(now.minus(10, ChronoUnit.MINUTES));
        Date last24h = Date.from(now.minus(24, ChronoUnit.HOURS));

        List<MetricSnapshot> metricSnapshots = metricSnapshotRepository.findSinceOrdered(last24h);
        List<AdminAnalyticsDashboard.ServerActivityPoint> serverActivity = metricSnapshots.stream()
            .map(s -> new AdminAnalyticsDashboard.ServerActivityPoint(s.getDate().toInstant().toString(), s.getActiveServers()))
            .toList();

        List<ServerInstanceSnapshot> instanceSnapshots = serverInstanceSnapshotRepository.findSinceOrdered(last24h);

        ServerInstanceSnapshot latestSnapshot = !instanceSnapshots.isEmpty()
            && !instanceSnapshots.getLast().getDate().before(snapshotCutoff)
            ? instanceSnapshots.getLast()
            : null;

        List<AdminAnalyticsDashboard.LiveServer> liveServers;
        if (latestSnapshot != null && latestSnapshot.getServers() != null && !latestSnapshot.getServers().isEmpty()) {
            List<String> serverIds = latestSnapshot.getServers().stream()
                .map(ServerInstanceSnapshot.ServerEntry::getServerId)
                .collect(Collectors.toList());
            Map<String, String> serverNameMap = serverUsageRepository.findUsageTargetsByIds(serverIds).stream()
                .collect(Collectors.toMap(Server::getId, Server::getServerName, (a, b) -> a));

            liveServers = latestSnapshot.getServers().stream()
                .map(srv -> new AdminAnalyticsDashboard.LiveServer(
                    srv.getServerId(),
                    serverNameMap.getOrDefault(srv.getServerId(), srv.getServerName()),
                    srv.getPlayerCount(),
                    srv.getPlatform(),
                    srv.getVersion(),
                    srv.getPluginVersion()))
                .toList();
        } else {
            liveServers = List.of();
        }

        int totalPlayerCount = latestSnapshot != null
            ? sumPlayerCount(latestSnapshot)
            : 0;

        List<AdminAnalyticsDashboard.PlayerActivityPoint> playerActivity = instanceSnapshots.stream()
            .map(s -> new AdminAnalyticsDashboard.PlayerActivityPoint(s.getDate().toInstant().toString(), sumPlayerCount(s)))
            .toList();

        AdminAnalyticsDashboard.Overview overview = new AdminAnalyticsDashboard.Overview(
            totalServers,
            activeServers,
            totalUsers,
            totalTickets,
            String.format("%.2f", serverGrowthRate),
            "0.00",
            String.format("%.1f", avgPlayersPerServer),
            String.format("%.1f", avgTicketsPerServer));

        AdminAnalyticsDashboard.ServerMetrics serverMetrics = new AdminAnalyticsDashboard.ServerMetrics(
            toNameCounts(planResults),
            toNameCounts(statusResults),
            toRegistrationPoints(registrationTrend));

        AdminAnalyticsDashboard.UsageStatistics usageStatistics = new AdminAnalyticsDashboard.UsageStatistics(
            topServers,
            serverActivity,
            liveServers,
            totalPlayerCount,
            playerActivity);

        return new AdminAnalyticsDashboard(overview, serverMetrics, usageStatistics);
    }

    private int sumPlayerCount(ServerInstanceSnapshot snapshot) {
        return snapshot.getServers() != null
            ? snapshot.getServers().stream().mapToInt(ServerInstanceSnapshot.ServerEntry::getPlayerCount).sum()
            : 0;
    }

    public AdminAnalyticsActivity getActivity(String range) {
        int days = DateRangeUtil.resolveRangeDays(range);
        Date startDate = Date.from(Instant.now().minus(days, ChronoUnit.DAYS));

        List<MetricSnapshot> snapshots = metricSnapshotRepository.findSinceOrdered(startDate);
        List<ServerInstanceSnapshot> instanceSnapshots = serverInstanceSnapshotRepository.findSinceOrdered(startDate);

        long totalServers = serverMetricsRepository.countAll();
        long totalPlayers = serverMetricsRepository.getUsageTotals().totalUsers();

        Map<String, Integer> playersByBucket = new HashMap<>();
        for (ServerInstanceSnapshot inst : instanceSnapshots) {
            String bucketKey = inst.getDate().toInstant().toString();
            int players = sumPlayerCount(inst);
            playersByBucket.merge(bucketKey, players, Math::max);
        }

        List<AdminAnalyticsActivity.ActivityPoint> activityData = snapshots.stream().map(s -> {
            String dateKey = s.getDate().toInstant().toString();
            int onlinePlayers = playersByBucket.getOrDefault(dateKey, 0);
            return new AdminAnalyticsActivity.ActivityPoint(dateKey, s.getActiveServers(), onlinePlayers);
        }).toList();

        return new AdminAnalyticsActivity(totalPlayers, totalServers, activityData);
    }

    public AdminAnalyticsUsage getUsage() {
        Date thirtyDaysAgo = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));

        long activeServers = serverMetricsRepository.countActiveSince(thirtyDaysAgo);
        long storageSize = globalMongoAdminRepository.getStorageSize();

        return new AdminAnalyticsUsage(activeServers, storageSize, 0.0, 0L, 0L);
    }

    public AdminAnalyticsHistorical getHistorical(String metric, String range) {
        if (metric == null || (!metric.equals("servers") && !metric.equals("users") && !metric.equals("tickets"))) {
            throw new ValidationException("Invalid metric type");
        }

        int days = DateRangeUtil.resolveRangeDays(range);
        Date startDate = Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
        List<DateValueResult> results = serverMetricsRepository.aggregateHistoricalMetric(metric, startDate);

        return new AdminAnalyticsHistorical(
            metric,
            range != null && !range.isBlank() ? range : "30d",
            results.stream().map(r -> new AdminHistoricalPoint(r.date(), r.value())).toList());
    }

    public String exportCsv(String range) {
        String normalizedRange = range != null && !range.isBlank() ? range : "30d";
        int days = DateRangeUtil.resolveRangeDays(normalizedRange);
        Date startDate = Date.from(Instant.now().minus(days, ChronoUnit.DAYS));
        return buildCsv(startDate);
    }

    public AdminAnalyticsExport exportJson(String range) {
        String normalizedRange = range != null && !range.isBlank() ? range : "30d";

        long totalServers = serverMetricsRepository.countAll();
        ServerMetricsRepository.UsageTotals totals = serverMetricsRepository.getUsageTotals();

        return new AdminAnalyticsExport(
            new Date().toString(),
            normalizedRange,
            totalServers,
            totals.totalUsers(),
            totals.totalTickets());
    }

    private String buildCsv(Date startDate) {
        List<DateValueResult> servers = serverMetricsRepository.aggregateHistoricalMetric("servers", startDate);
        List<DateValueResult> users = serverMetricsRepository.aggregateHistoricalMetric("users", startDate);
        List<DateValueResult> tickets = serverMetricsRepository.aggregateHistoricalMetric("tickets", startDate);

        TreeMap<String, long[]> byDate = new TreeMap<>();
        for (DateValueResult r : servers) {
            byDate.computeIfAbsent(r.date(), k -> new long[3])[0] = r.value();
        }
        for (DateValueResult r : users) {
            byDate.computeIfAbsent(r.date(), k -> new long[3])[1] = r.value();
        }
        for (DateValueResult r : tickets) {
            byDate.computeIfAbsent(r.date(), k -> new long[3])[2] = r.value();
        }

        StringBuilder sb = new StringBuilder("Date,Servers,Users,Tickets\n");
        for (Map.Entry<String, long[]> entry : byDate.entrySet()) {
            long[] v = entry.getValue();
            sb.append(entry.getKey()).append(",")
                .append(v[0]).append(",")
                .append(v[1]).append(",")
                .append(v[2]).append("\n");
        }
        return sb.toString();
    }

    private static List<AdminNameCount> toNameCounts(List<NameValueResult> results) {
        return results.stream().map(r -> new AdminNameCount(r.name(), r.value())).toList();
    }

    private static List<AdminRegistrationPoint> toRegistrationPoints(List<DateServersResult> results) {
        return results.stream().map(r -> new AdminRegistrationPoint(r.date(), r.servers())).toList();
    }
}
