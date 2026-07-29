package gg.modl.backend.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import gg.modl.backend.admin.data.SecurityEvent;
import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.admin.data.SystemLog;
import gg.modl.backend.admin.dto.response.AdminAnalyticsActivity;
import gg.modl.backend.admin.dto.response.AdminAnalyticsDashboard;
import gg.modl.backend.admin.dto.response.AdminAnalyticsExport;
import gg.modl.backend.admin.dto.response.AdminAnalyticsHistorical;
import gg.modl.backend.admin.dto.response.AdminAnalyticsUsage;
import gg.modl.backend.admin.dto.response.AdminHistoricalPoint;
import gg.modl.backend.admin.dto.response.AdminMaintenanceStatus;
import gg.modl.backend.admin.dto.response.AdminMonitoringDashboard;
import gg.modl.backend.admin.dto.response.AdminMonitoringHealth;
import gg.modl.backend.admin.dto.response.AdminMonitoringLogs;
import gg.modl.backend.admin.dto.response.AdminMonitoringSources;
import gg.modl.backend.admin.dto.response.AdminNameCount;
import gg.modl.backend.admin.dto.response.AdminPagination;
import gg.modl.backend.admin.dto.response.AdminRateLimitStatus;
import gg.modl.backend.admin.dto.response.AdminRegistrationPoint;
import gg.modl.backend.admin.dto.response.AdminSecurityEvents;
import gg.modl.backend.admin.dto.response.AdminSecuritySummary;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminProtoMapperGoldenTest {

    private static final Path GOLDEN_DIR = Path.of("src/test/resources/admin/golden");
    private static final java.util.Date FIXED_A = new java.util.Date(1_700_000_000_000L);
    private static final java.util.Date FIXED_B = new java.util.Date(1_700_000_100_000L);

    private static void verify(String name, Message message) throws IOException {
        String actual = TextFormat.printer().printToString(message);
        assertEquals(Files.readString(GOLDEN_DIR.resolve(name + ".txtpb")), actual, name);
    }

    @Test
    void analyticsDashboard() throws IOException {
        verify("analytics_dashboard_full", AdminAnalyticsProtoMapper.toDashboardResponse(dashboard(true)));
        verify("analytics_dashboard_empty", AdminAnalyticsProtoMapper.toDashboardResponse(dashboard(false)));
    }

    @Test
    void analyticsActivity() throws IOException {
        AdminAnalyticsActivity full = new AdminAnalyticsActivity(500L, 10L, List.of(
            new AdminAnalyticsActivity.ActivityPoint("2026-07-01T00:00:00Z", 5L, 30),
            new AdminAnalyticsActivity.ActivityPoint("2026-07-02T00:00:00Z", 7L, 42)));
        verify("analytics_activity_full", AdminAnalyticsProtoMapper.toActivityResponse(full));

        AdminAnalyticsActivity empty = new AdminAnalyticsActivity(0L, 0L, List.of());
        verify("analytics_activity_empty", AdminAnalyticsProtoMapper.toActivityResponse(empty));
    }

    @Test
    void analyticsUsage() throws IOException {
        AdminAnalyticsUsage usage = new AdminAnalyticsUsage(8L, 2048L, 0.0, 0L, 0L);
        verify("analytics_usage", AdminAnalyticsProtoMapper.toUsageResponse(usage));
    }

    @Test
    void analyticsHistorical() throws IOException {
        AdminAnalyticsHistorical historical = new AdminAnalyticsHistorical("servers", "30d", List.of(
            new AdminHistoricalPoint("2026-07-01", 3L),
            new AdminHistoricalPoint("2026-07-02", 6L)));
        verify("analytics_historical", AdminAnalyticsProtoMapper.toHistoricalResponse(historical));
    }

    @Test
    void analyticsExport() throws IOException {
        AdminAnalyticsExport export = new AdminAnalyticsExport("Fri Jul 11 00:00:00 UTC 2026", "30d", 10L, 500L, 42L);
        verify("analytics_export", AdminAnalyticsProtoMapper.toExportResponse(export));
    }

    @Test
    void monitoringDashboard() throws IOException {
        AdminMonitoringDashboard dashboard = new AdminMonitoringDashboard(
            new AdminMonitoringDashboard.ServerMetrics(10L, 7L, 1L, 2L, 3L, 4L, 55L),
            new AdminMonitoringDashboard.LogMetrics(
                new AdminMonitoringDashboard.LogWindow(20L, 1L, 3L, 5L),
                new AdminMonitoringDashboard.UnresolvedLogs(1L, 2L)),
            new AdminMonitoringDashboard.SystemHealth(88, "good"),
            List.of(Map.of(
                "_id", "2026-07-01",
                "total", 5,
                "levels", List.of(Map.of("level", "error", "count", 2)))),
            FIXED_A);
        verify("monitoring_dashboard", AdminMonitoringProtoMapper.toDashboardResponse(dashboard));
    }

    @Test
    void monitoringLogs() throws IOException {
        SystemLog log = new SystemLog();
        log.setId("log-1");
        log.setLevel("warning");
        log.setMessage("CPU spike");
        log.setSource("monitor");
        log.setCategory("infra");
        log.setServerId("srv-1");
        log.setMetadata(new LinkedHashMap<>(Map.of("cpu", 95)));
        log.setResolved(true);
        log.setResolvedBy("admin");
        log.setResolvedAt(FIXED_B);
        log.setTimestamp(FIXED_A);

        AdminMonitoringLogs full = new AdminMonitoringLogs(
            List.of(log),
            new AdminPagination(1, 50, 1L, 1),
            new AdminMonitoringLogs.Filters("warning", "monitor", "srv-1", "infra", "true", "cpu"));
        verify("monitoring_logs_full", AdminMonitoringProtoMapper.toLogsResponse(full));

        AdminMonitoringLogs minimal = new AdminMonitoringLogs(
            List.of(),
            new AdminPagination(1, 50, 0L, 0),
            new AdminMonitoringLogs.Filters(null, null, null, null, null, null));
        verify("monitoring_logs_null_filters", AdminMonitoringProtoMapper.toLogsResponse(minimal));
    }

    @Test
    void monitoringSources() throws IOException {
        AdminMonitoringSources sources = new AdminMonitoringSources(
            List.of("monitor", "gateway"),
            List.of("infra", "security"));
        verify("monitoring_sources", AdminMonitoringProtoMapper.toSourcesResponse(sources));
    }

    @Test
    void monitoringHealth() throws IOException {
        AdminMonitoringHealth health = new AdminMonitoringHealth("degraded", List.of(
            AdminMonitoringHealth.HealthCheck.responsive(
                "Database Connectivity", "healthy", "MongoDB connection is responsive.", 12L),
            AdminMonitoringHealth.HealthCheck.failure(
                "Database Connectivity", "critical", "Failed to ping MongoDB.", "timeout"),
            AdminMonitoringHealth.HealthCheck.counted(
                "Critical System Logs", "degraded", "2 unresolved critical log(s) in the last 24 hours.", 2L),
            AdminMonitoringHealth.HealthCheck.counted(
                "Server Provisioning", "healthy", "0 server(s) failed to provision.", 0L)),
            FIXED_A);
        verify("monitoring_health", AdminMonitoringProtoMapper.toHealthResponse(health));
    }

    @Test
    void securityEvents() throws IOException {
        SecurityEvent event = new SecurityEvent();
        event.setId("evt-1");
        event.setType("login_attempt");
        event.setSeverity("high");
        event.setSource("gateway");
        event.setDescription("blocked login");
        event.setTimestamp(FIXED_A);

        AdminSecurityEvents events = new AdminSecurityEvents(List.of(event), new AdminPagination(2, 25, 1L, 1));
        verify("security_events", AdminSecurityProtoMapper.toEventsResponse(events));
    }

    @Test
    void securitySummary() throws IOException {
        AdminSecuritySummary summary = new AdminSecuritySummary(
            new AdminSecuritySummary.Last24Hours(1L, 2L, 3L), 10L, FIXED_A);
        verify("security_summary", AdminSecurityProtoMapper.toSummaryResponse(summary));
    }

    @Test
    void systemMaintenance() throws IOException {
        verify("system_maintenance_with_message", AdminSystemProtoMapper.toMaintenanceResponse(
            new AdminMaintenanceStatus(true, "System under maintenance."), "Maintenance mode enabled"));
        verify("system_maintenance_null_message", AdminSystemProtoMapper.toMaintenanceResponse(
            new AdminMaintenanceStatus(false, null), null));
    }

    @Test
    void systemRateLimits() throws IOException {
        SystemConfig.PerformanceConfig performance = new SystemConfig.PerformanceConfig();
        performance.setRateLimitRequests(1000);
        performance.setRateLimitWindow(900);
        performance.setCacheTtl(60);
        performance.setDatabaseConnectionPool(20);
        performance.setEnableCompression(true);
        performance.setEnableCaching(true);

        verify("system_rate_limits", AdminSystemProtoMapper.toRateLimitsResponse(
            new AdminRateLimitStatus(performance, true, FIXED_B)));
    }

    private static AdminAnalyticsDashboard dashboard(boolean populated) {
        AdminAnalyticsDashboard.Overview overview = new AdminAnalyticsDashboard.Overview(
            10L, 7L, 500L, 42L, "12.34", "0.00", "5.0", "2.0");

        AdminAnalyticsDashboard.ServerMetrics serverMetrics = populated
            ? new AdminAnalyticsDashboard.ServerMetrics(
                List.of(new AdminNameCount("free", 6), new AdminNameCount("premium", 4)),
                List.of(new AdminNameCount("completed", 8)),
                List.of(new AdminRegistrationPoint("2026-07-01", 3), new AdminRegistrationPoint("2026-07-02", 5)))
            : new AdminAnalyticsDashboard.ServerMetrics(List.of(), List.of(), List.of());

        AdminAnalyticsDashboard.UsageStatistics usageStatistics;
        if (populated) {
            Server topServer = new Server("Alpha", "alpha.modl.gg", "db_alpha", "admin@x.com", true, ServerPlan.PREMIUM);
            topServer.setId("srv-1");
            usageStatistics = new AdminAnalyticsDashboard.UsageStatistics(
                List.of(topServer),
                List.of(new AdminAnalyticsDashboard.ServerActivityPoint("2026-07-01T00:00:00Z", 5L)),
                List.of(
                    new AdminAnalyticsDashboard.LiveServer("srv-1", "Alpha", 12, "paper", "1.21", "2.2.0"),
                    new AdminAnalyticsDashboard.LiveServer("srv-2", "Beta", 0, null, null, null)),
                42,
                List.of(new AdminAnalyticsDashboard.PlayerActivityPoint("2026-07-01T00:00:00Z", 30)));
        } else {
            usageStatistics = new AdminAnalyticsDashboard.UsageStatistics(
                List.of(), List.of(), List.of(), 0, List.of());
        }

        return new AdminAnalyticsDashboard(overview, serverMetrics, usageStatistics);
    }
}
