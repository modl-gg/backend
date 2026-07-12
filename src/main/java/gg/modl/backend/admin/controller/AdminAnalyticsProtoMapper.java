package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.dto.response.AdminAnalyticsActivity;
import gg.modl.backend.admin.dto.response.AdminAnalyticsDashboard;
import gg.modl.backend.admin.dto.response.AdminAnalyticsExport;
import gg.modl.backend.admin.dto.response.AdminAnalyticsHistorical;
import gg.modl.backend.admin.dto.response.AdminAnalyticsUsage;
import gg.modl.backend.admin.dto.response.AdminNameCount;
import gg.modl.backend.admin.dto.response.AdminRegistrationPoint;
import gg.modl.proto.modl.v1.AdminAnalyticsActivityPoint;
import gg.modl.proto.modl.v1.AdminAnalyticsActivityResponse;
import gg.modl.proto.modl.v1.AdminAnalyticsDashboardData;
import gg.modl.proto.modl.v1.AdminAnalyticsDashboardResponse;
import gg.modl.proto.modl.v1.AdminAnalyticsDateServers;
import gg.modl.proto.modl.v1.AdminAnalyticsDateValue;
import gg.modl.proto.modl.v1.AdminAnalyticsExportData;
import gg.modl.proto.modl.v1.AdminAnalyticsExportResponse;
import gg.modl.proto.modl.v1.AdminAnalyticsHistoricalData;
import gg.modl.proto.modl.v1.AdminAnalyticsHistoricalResponse;
import gg.modl.proto.modl.v1.AdminAnalyticsLiveServer;
import gg.modl.proto.modl.v1.AdminAnalyticsNameValue;
import gg.modl.proto.modl.v1.AdminAnalyticsOverview;
import gg.modl.proto.modl.v1.AdminAnalyticsPlayerActivity;
import gg.modl.proto.modl.v1.AdminAnalyticsResourceUtilization;
import gg.modl.proto.modl.v1.AdminAnalyticsServerActivity;
import gg.modl.proto.modl.v1.AdminAnalyticsServerMetrics;
import gg.modl.proto.modl.v1.AdminAnalyticsSystemHealth;
import gg.modl.proto.modl.v1.AdminAnalyticsUsageData;
import gg.modl.proto.modl.v1.AdminAnalyticsUsageResponse;
import gg.modl.proto.modl.v1.AdminAnalyticsUsageStatistics;
import gg.modl.proto.modl.v1.AdminAnalyticsUserEngagement;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;

final class AdminAnalyticsProtoMapper {

    private AdminAnalyticsProtoMapper() {
    }

    static AdminAnalyticsDashboardResponse toDashboardResponse(AdminAnalyticsDashboard data) {
        AdminAnalyticsDashboardData dashboard = AdminAnalyticsDashboardData.newBuilder()
            .setOverview(toOverview(data.overview()))
            .setServerMetrics(toServerMetrics(data.serverMetrics()))
            .setUsageStatistics(toUsageStatistics(data.usageStatistics()))
            .setSystemHealth(AdminAnalyticsSystemHealth.newBuilder().build())
            .build();
        return AdminAnalyticsDashboardResponse.newBuilder()
            .setSuccess(true)
            .setData(dashboard)
            .build();
    }

    static AdminAnalyticsActivityResponse toActivityResponse(AdminAnalyticsActivity response) {
        AdminAnalyticsActivityResponse.Builder builder = AdminAnalyticsActivityResponse.newBuilder()
            .setSuccess(true)
            .setTotalPlayers(response.totalPlayers())
            .setTotalServers(response.totalServers());
        response.data().forEach(point -> builder.addData(AdminAnalyticsActivityPoint.newBuilder()
            .setDate(stringValue(point.date()))
            .setActiveServers(point.activeServers())
            .setOnlinePlayers(point.onlinePlayers())
            .build()));
        return builder.build();
    }

    static AdminAnalyticsUsageResponse toUsageResponse(AdminAnalyticsUsage response) {
        AdminAnalyticsUsageData usageData = AdminAnalyticsUsageData.newBuilder()
            .setUserEngagement(AdminAnalyticsUserEngagement.newBuilder()
                .setMonthlyActiveServers(response.monthlyActiveServers())
                .build())
            .setResourceUtilization(AdminAnalyticsResourceUtilization.newBuilder()
                .setStorage(response.storage())
                .setStoragePercent(response.storagePercent())
                .setApiCalls(response.apiCalls())
                .setDatabaseQueries(response.databaseQueries())
                .build())
            .build();
        return AdminAnalyticsUsageResponse.newBuilder()
            .setSuccess(true)
            .setData(usageData)
            .build();
    }

    static AdminAnalyticsHistoricalResponse toHistoricalResponse(AdminAnalyticsHistorical response) {
        AdminAnalyticsHistoricalData.Builder dataBuilder = AdminAnalyticsHistoricalData.newBuilder()
            .setMetric(stringValue(response.metric()))
            .setRange(stringValue(response.range()));
        response.data().forEach(point -> dataBuilder.addData(AdminAnalyticsDateValue.newBuilder()
            .setDate(stringValue(point.date()))
            .setValue(point.value())
            .build()));
        return AdminAnalyticsHistoricalResponse.newBuilder()
            .setSuccess(true)
            .setData(dataBuilder.build())
            .build();
    }

    static AdminAnalyticsExportResponse toExportResponse(AdminAnalyticsExport response) {
        return AdminAnalyticsExportResponse.newBuilder()
            .setExportDate(stringValue(response.exportDate()))
            .setRange(stringValue(response.range()))
            .setData(AdminAnalyticsExportData.newBuilder()
                .setServers(response.servers())
                .setUsers(response.users())
                .setTickets(response.tickets())
                .build())
            .build();
    }

    private static AdminAnalyticsOverview toOverview(AdminAnalyticsDashboard.Overview overview) {
        return AdminAnalyticsOverview.newBuilder()
            .setTotalServers(overview.totalServers())
            .setActiveServers(overview.activeServers())
            .setTotalUsers(overview.totalUsers())
            .setTotalTickets(overview.totalTickets())
            .setServerGrowthRate(overview.serverGrowthRate())
            .setUserGrowthRate(overview.userGrowthRate())
            .setAvgPlayersPerServer(overview.avgPlayersPerServer())
            .setAvgTicketsPerServer(overview.avgTicketsPerServer())
            .build();
    }

    private static AdminAnalyticsServerMetrics toServerMetrics(AdminAnalyticsDashboard.ServerMetrics serverMetrics) {
        AdminAnalyticsServerMetrics.Builder builder = AdminAnalyticsServerMetrics.newBuilder();
        serverMetrics.byPlan().forEach(count -> builder.addByPlan(toNameValue(count)));
        serverMetrics.byStatus().forEach(count -> builder.addByStatus(toNameValue(count)));
        serverMetrics.registrationTrend().forEach(point -> builder.addRegistrationTrend(toDateServers(point)));
        return builder.build();
    }

    private static AdminAnalyticsUsageStatistics toUsageStatistics(AdminAnalyticsDashboard.UsageStatistics usageStatistics) {
        AdminAnalyticsUsageStatistics.Builder builder = AdminAnalyticsUsageStatistics.newBuilder()
            .setTotalPlayerCount(usageStatistics.totalPlayerCount());
        usageStatistics.topServersByUsers().forEach(server ->
            builder.addTopServersByUsers(AdminServerProtoMapper.toRecord(server)));
        usageStatistics.serverActivity().forEach(activity ->
            builder.addServerActivity(AdminAnalyticsServerActivity.newBuilder()
                .setDate(stringValue(activity.date()))
                .setActiveServers(activity.activeServers())
                .build()));
        usageStatistics.liveServers().forEach(live -> builder.addLiveServers(toLiveServer(live)));
        usageStatistics.playerActivity().forEach(activity ->
            builder.addPlayerActivity(AdminAnalyticsPlayerActivity.newBuilder()
                .setDate(stringValue(activity.date()))
                .setPlayers(activity.players())
                .build()));
        return builder.build();
    }

    private static AdminAnalyticsLiveServer toLiveServer(AdminAnalyticsDashboard.LiveServer live) {
        AdminAnalyticsLiveServer.Builder builder = AdminAnalyticsLiveServer.newBuilder()
            .setServerId(stringValue(live.serverId()))
            .setServerName(stringValue(live.serverName()))
            .setPlayerCount(live.playerCount());
        if (live.platform() != null) {
            builder.setPlatform(live.platform());
        }
        if (live.version() != null) {
            builder.setVersion(live.version());
        }
        if (live.pluginVersion() != null) {
            builder.setPluginVersion(live.pluginVersion());
        }
        return builder.build();
    }

    private static AdminAnalyticsNameValue toNameValue(AdminNameCount count) {
        return AdminAnalyticsNameValue.newBuilder()
            .setName(stringValue(count.name()))
            .setValue(count.value())
            .build();
    }

    private static AdminAnalyticsDateServers toDateServers(AdminRegistrationPoint point) {
        return AdminAnalyticsDateServers.newBuilder()
            .setDate(stringValue(point.date()))
            .setServers(point.servers())
            .build();
    }
}
