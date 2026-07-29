package gg.modl.backend.admin.dto.response;

import java.util.Date;
import java.util.List;
import java.util.Map;

public record AdminMonitoringDashboard(
    ServerMetrics servers,
    LogMetrics logs,
    SystemHealth systemHealth,
    List<Map<String, Object>> trends,
    Date lastUpdated
) {
    public record ServerMetrics(
        long total,
        long active,
        long pending,
        long failed,
        long recentRegistrations,
        long concurrentServers,
        long concurrentPlayers
    ) {
    }

    public record LogMetrics(
        LogWindow last24h,
        UnresolvedLogs unresolved
    ) {
    }

    public record LogWindow(
        long total,
        long critical,
        long error,
        long warning
    ) {
    }

    public record UnresolvedLogs(
        long critical,
        long error
    ) {
    }

    public record SystemHealth(
        int score,
        String status
    ) {
    }
}
