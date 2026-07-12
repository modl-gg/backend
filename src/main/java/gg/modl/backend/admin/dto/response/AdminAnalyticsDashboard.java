package gg.modl.backend.admin.dto.response;

import gg.modl.backend.server.data.Server;
import java.util.List;

public record AdminAnalyticsDashboard(
    Overview overview,
    ServerMetrics serverMetrics,
    UsageStatistics usageStatistics
) {
    public record Overview(
        long totalServers,
        long activeServers,
        long totalUsers,
        long totalTickets,
        String serverGrowthRate,
        String userGrowthRate,
        String avgPlayersPerServer,
        String avgTicketsPerServer
    ) {
    }

    public record ServerMetrics(
        List<AdminNameCount> byPlan,
        List<AdminNameCount> byStatus,
        List<AdminRegistrationPoint> registrationTrend
    ) {
    }

    public record UsageStatistics(
        List<Server> topServersByUsers,
        List<ServerActivityPoint> serverActivity,
        List<LiveServer> liveServers,
        int totalPlayerCount,
        List<PlayerActivityPoint> playerActivity
    ) {
    }

    public record ServerActivityPoint(
        String date,
        long activeServers
    ) {
    }

    public record LiveServer(
        String serverId,
        String serverName,
        int playerCount,
        String platform,
        String version,
        String pluginVersion
    ) {
    }

    public record PlayerActivityPoint(
        String date,
        int players
    ) {
    }
}
