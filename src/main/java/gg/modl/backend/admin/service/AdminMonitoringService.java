package gg.modl.backend.admin.service;

import gg.modl.backend.admin.data.SystemLog;
import gg.modl.backend.admin.dto.request.CreateSystemLogRequest;
import gg.modl.backend.admin.dto.request.ResolveLogRequest;
import gg.modl.backend.admin.dto.response.AdminMonitoringDashboard;
import gg.modl.backend.admin.dto.response.AdminMonitoringHealth;
import gg.modl.backend.admin.dto.response.AdminMonitoringLogs;
import gg.modl.backend.admin.dto.response.AdminMonitoringSources;
import gg.modl.backend.admin.dto.response.AdminPagination;
import gg.modl.backend.database.mongo.repository.GlobalMongoAdminRepository;
import gg.modl.backend.database.mongo.repository.ServerMetricsRepository;
import gg.modl.backend.database.mongo.repository.SystemLogMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerMetricsRepository.MonitoringServerStats;
import gg.modl.backend.database.mongo.repository.SystemLogMongoRepository.MonitoringLogStats;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.infrastructure.util.CsvUtil;
import gg.modl.backend.infrastructure.util.DateRangeUtil;
import gg.modl.backend.infrastructure.util.PaginationHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMonitoringService {
    private final SystemLogMongoRepository systemLogRepository;
    private final ServerMetricsRepository serverMetricsRepository;
    private final GlobalMongoAdminRepository globalMongoAdminRepository;

    public AdminMonitoringDashboard getDashboard() {
        Date oneDayAgo = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date oneWeekAgo = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));
        Date fiveMinutesAgo = Date.from(Instant.now().minus(5, ChronoUnit.MINUTES));

        MonitoringServerStats serverStats = serverMetricsRepository.aggregateMonitoringServerStats(fiveMinutesAgo, oneWeekAgo);
        MonitoringLogStats logStats = systemLogRepository.aggregateMonitoringLogStats(oneDayAgo);

        int healthScore = calculateHealthScore(serverStats, logStats);
        String healthStatus = healthScore >= 95 ? "excellent"
                                                : healthScore >= 85 ? "good"
                                                                    : healthScore >= 70 ? "fair"
                                                                                        : "poor";

        List<Map<String, Object>> trends = new ArrayList<>(systemLogRepository.findLogTrends(oneWeekAgo));

        return new AdminMonitoringDashboard(
            new AdminMonitoringDashboard.ServerMetrics(
                serverStats.total(),
                serverStats.active(),
                serverStats.pending(),
                serverStats.failed(),
                serverStats.recentRegistrations(),
                serverStats.concurrent(),
                serverStats.concurrentPlayers()),
            new AdminMonitoringDashboard.LogMetrics(
                new AdminMonitoringDashboard.LogWindow(
                    logStats.total24h(),
                    logStats.critical24h(),
                    logStats.error24h(),
                    logStats.warning24h()),
                new AdminMonitoringDashboard.UnresolvedLogs(
                    logStats.unresolvedCritical(),
                    logStats.unresolvedError())),
            new AdminMonitoringDashboard.SystemHealth(healthScore, healthStatus),
            trends,
            new Date());
    }

    private int calculateHealthScore(MonitoringServerStats serverStats, MonitoringLogStats logStats) {
        int score = 100;
        if (serverStats.total() > 0) {
            score -= (int) ((serverStats.failed() / (double) serverStats.total()) * 30);
        }
        score -= (int) Math.min(logStats.critical24h() * 5, 25);
        score -= (int) Math.min(logStats.error24h(), 20);
        score -= (int) (logStats.unresolvedCritical() * 10);
        score -= (int) (logStats.unresolvedError() * 3);
        return Math.max(0, score);
    }

    public AdminMonitoringLogs getLogs(
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
        int pageNum = PaginationHelper.normalizePage(page);
        int limitNum = PaginationHelper.normalizeLimit(limit, 100);
        int skip = PaginationHelper.calculateSkip(page, limitNum);
        Date start = DateRangeUtil.parseEpochMillis(startDate);
        Date end = DateRangeUtil.parseEpochMillis(endDate);

        List<SystemLog> logs = systemLogRepository.findLogs(
            level,
            source,
            serverId,
            category,
            resolved,
            search,
            start,
            end,
            sort,
            order,
            skip,
            limitNum
        );
        long total = systemLogRepository.countLogs(level, source, serverId, category, resolved, search, start, end);

        return new AdminMonitoringLogs(
            logs,
            new AdminPagination(pageNum, limitNum, total, PaginationHelper.calculateTotalPages(total, limitNum)),
            new AdminMonitoringLogs.Filters(level, source, serverId, category, resolved, search));
    }

    public SystemLog createLog(CreateSystemLogRequest request) {
        SystemLog logData = request.toSystemLog();
        logData.setTimestamp(new Date());
        return systemLogRepository.saveEntity(logData);
    }

    public AdminMonitoringSources getSources() {
        List<String> sources = systemLogRepository.findDistinctSources();
        List<String> categories = systemLogRepository.findDistinctCategories();

        sources.removeIf(Objects::isNull);
        categories.removeIf(Objects::isNull);

        return new AdminMonitoringSources(sources, categories);
    }

    public Optional<SystemLog> resolveLog(String id, ResolveLogRequest request) {
        return Optional.ofNullable(systemLogRepository.resolveById(
            id,
            request.resolvedBy() != null ? request.resolvedBy() : "admin",
            new Date()
        ));
    }

    public AdminMonitoringHealth getHealth() {
        List<AdminMonitoringHealth.HealthCheck> checks = new ArrayList<>();
        String overallStatus = "healthy";

        try {
            long start = System.currentTimeMillis();
            globalMongoAdminRepository.ping();
            long responseTime = System.currentTimeMillis() - start;
            checks.add(AdminMonitoringHealth.HealthCheck.responsive(
                "Database Connectivity",
                "healthy",
                "MongoDB connection is responsive.",
                responseTime));
        } catch (Exception exception) {
            checks.add(AdminMonitoringHealth.HealthCheck.failure(
                "Database Connectivity",
                "critical",
                "Failed to ping MongoDB.",
                exception.getMessage()));
            overallStatus = "critical";
        }

        long criticalCount = systemLogRepository.countUnresolvedByLevelSince(
            "critical",
            Date.from(Instant.now().minus(1, ChronoUnit.DAYS))
        );
        String logStatus = criticalCount > 5 ? "critical" : criticalCount > 0 ? "degraded" : "healthy";
        checks.add(AdminMonitoringHealth.HealthCheck.counted(
            "Critical System Logs",
            logStatus,
            criticalCount + " unresolved critical log(s) in the last 24 hours.",
            criticalCount));
        if ("critical".equals(logStatus)) {
            overallStatus = "critical";
        } else if ("degraded".equals(logStatus) && !"critical".equals(overallStatus)) {
            overallStatus = "degraded";
        }

        long failedCount = serverMetricsRepository.countByProvisioningStatus(ProvisioningStatus.FAILED);
        String serverStatus = failedCount > 0 ? "degraded" : "healthy";
        checks.add(AdminMonitoringHealth.HealthCheck.counted(
            "Server Provisioning",
            serverStatus,
            failedCount + " server(s) failed to provision.",
            failedCount));
        if ("degraded".equals(serverStatus) && !"critical".equals(overallStatus)) {
            overallStatus = "degraded";
        }

        return new AdminMonitoringHealth(overallStatus, checks, new Date());
    }

    public long deleteLogs(List<String> logIds) {
        return systemLogRepository.deleteByIds(logIds);
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
        List<SystemLog> logs = systemLogRepository.findLogsForExport(
            DateRangeUtil.normalizeAllFilter(level),
            DateRangeUtil.normalizeAllFilter(source),
            null,
            DateRangeUtil.normalizeAllFilter(category),
            DateRangeUtil.normalizeAllFilter(resolved),
            search,
            DateRangeUtil.parseEpochMillis(startDate),
            DateRangeUtil.parseEpochMillis(endDate),
            10000
        );

        StringBuilder csv = new StringBuilder(CsvUtil.row("Timestamp", "Level", "Source", "Category", "Message", "Resolved", "Resolved By"));
        for (SystemLog logEntry : logs) {
            csv.append(CsvUtil.row(
                logEntry.getTimestamp(),
                logEntry.getLevel(),
                logEntry.getSource(),
                logEntry.getCategory(),
                logEntry.getMessage(),
                logEntry.isResolved() ? "Yes" : "No",
                logEntry.getResolvedBy()));
        }
        return csv.toString();
    }

    public long clearAllLogs() {
        long deletedCount = systemLogRepository.deleteAllLogs();
        log.info("All system logs cleared by admin");
        return deletedCount;
    }
}
