package gg.modl.backend.admin.controller;

import gg.modl.backend.server.data.Server;
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
import gg.modl.proto.modl.v1.AdminAnalyticsServerActivity;
import gg.modl.proto.modl.v1.AdminAnalyticsServerMetrics;
import gg.modl.proto.modl.v1.AdminAnalyticsSystemHealth;
import gg.modl.proto.modl.v1.AdminAnalyticsUsageData;
import gg.modl.proto.modl.v1.AdminAnalyticsUsageResponse;
import gg.modl.proto.modl.v1.AdminAnalyticsUsageStatistics;
import gg.modl.proto.modl.v1.AdminAnalyticsUserEngagement;
import gg.modl.proto.modl.v1.AdminAnalyticsResourceUtilization;

import java.util.List;
import java.util.Map;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.doubleValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.intValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.list;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.listOfMaps;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.map;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;

final class AdminAnalyticsProtoMapper {

    private AdminAnalyticsProtoMapper() {
    }

    static AdminAnalyticsDashboardResponse toDashboardResponse(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        Map<String, Object> overview = map(data.get("overview"));
        Map<String, Object> serverMetrics = map(data.get("serverMetrics"));
        Map<String, Object> usageStatistics = map(data.get("usageStatistics"));

        AdminAnalyticsDashboardData.Builder builder = AdminAnalyticsDashboardData.newBuilder()
            .setOverview(toOverview(overview))
            .setServerMetrics(toServerMetrics(serverMetrics))
            .setUsageStatistics(toUsageStatistics(usageStatistics))
            .setSystemHealth(AdminAnalyticsSystemHealth.newBuilder().build());
        return AdminAnalyticsDashboardResponse.newBuilder()
            .setSuccess(true)
            .setData(builder.build())
            .build();
    }

    static AdminAnalyticsActivityResponse toActivityResponse(Map<String, Object> response) {
        AdminAnalyticsActivityResponse.Builder builder = AdminAnalyticsActivityResponse.newBuilder()
            .setSuccess(true)
            .setTotalPlayers(longValue(response.get("totalPlayers")))
            .setTotalServers(longValue(response.get("totalServers")));
        listOfMaps(response.get("data")).forEach(point -> builder.addData(AdminAnalyticsActivityPoint.newBuilder()
            .setDate(stringValue(point.get("date")))
            .setActiveServers(longValue(point.get("activeServers")))
            .setOnlinePlayers(intValue(point.get("onlinePlayers")))
            .build()));
        return builder.build();
    }

    static AdminAnalyticsUsageResponse toUsageResponse(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        Map<String, Object> userEngagement = map(data.get("userEngagement"));
        Map<String, Object> resourceUtilization = map(data.get("resourceUtilization"));

        AdminAnalyticsUsageData usageData = AdminAnalyticsUsageData.newBuilder()
            .setUserEngagement(AdminAnalyticsUserEngagement.newBuilder()
                .setMonthlyActiveServers(longValue(userEngagement.get("monthlyActiveServers")))
                .build())
            .setResourceUtilization(AdminAnalyticsResourceUtilization.newBuilder()
                .setStorage(longValue(resourceUtilization.get("storage")))
                .setStoragePercent(doubleValue(resourceUtilization.get("storagePercent")))
                .setApiCalls(longValue(resourceUtilization.get("apiCalls")))
                .setDatabaseQueries(longValue(resourceUtilization.get("databaseQueries")))
                .build())
            .build();
        return AdminAnalyticsUsageResponse.newBuilder()
            .setSuccess(true)
            .setData(usageData)
            .build();
    }

    static AdminAnalyticsHistoricalResponse toHistoricalResponse(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        AdminAnalyticsHistoricalData.Builder dataBuilder = AdminAnalyticsHistoricalData.newBuilder()
            .setMetric(stringValue(data.get("metric")))
            .setRange(stringValue(data.get("range")));
        list(data.get("data")).forEach(entry -> {
            Map<String, Object> point = map(entry);
            dataBuilder.addData(AdminAnalyticsDateValue.newBuilder()
                .setDate(stringValue(readField(entry, point, "date")))
                .setValue(longValue(readField(entry, point, "value")))
                .build());
        });
        return AdminAnalyticsHistoricalResponse.newBuilder()
            .setSuccess(true)
            .setData(dataBuilder.build())
            .build();
    }

    static AdminAnalyticsExportResponse toExportResponse(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        return AdminAnalyticsExportResponse.newBuilder()
            .setExportDate(stringValue(response.get("exportDate")))
            .setRange(stringValue(response.get("range")))
            .setData(AdminAnalyticsExportData.newBuilder()
                .setServers(longValue(data.get("servers")))
                .setUsers(longValue(data.get("users")))
                .setTickets(longValue(data.get("tickets")))
                .build())
            .build();
    }

    private static AdminAnalyticsOverview toOverview(Map<String, Object> overview) {
        return AdminAnalyticsOverview.newBuilder()
            .setTotalServers(longValue(overview.get("totalServers")))
            .setActiveServers(longValue(overview.get("activeServers")))
            .setTotalUsers(longValue(overview.get("totalUsers")))
            .setTotalTickets(longValue(overview.get("totalTickets")))
            .setServerGrowthRate(stringValue(overview.get("serverGrowthRate")))
            .setUserGrowthRate(stringValue(overview.get("userGrowthRate")))
            .setAvgPlayersPerServer(stringValue(overview.get("avgPlayersPerServer")))
            .setAvgTicketsPerServer(stringValue(overview.get("avgTicketsPerServer")))
            .build();
    }

    private static AdminAnalyticsServerMetrics toServerMetrics(Map<String, Object> serverMetrics) {
        AdminAnalyticsServerMetrics.Builder builder = AdminAnalyticsServerMetrics.newBuilder();
        toNameValues(serverMetrics.get("byPlan")).forEach(builder::addByPlan);
        toNameValues(serverMetrics.get("byStatus")).forEach(builder::addByStatus);
        list(serverMetrics.get("registrationTrend")).forEach(entry ->
            builder.addRegistrationTrend(toDateServers(entry)));
        return builder.build();
    }

    private static AdminAnalyticsUsageStatistics toUsageStatistics(Map<String, Object> usageStatistics) {
        AdminAnalyticsUsageStatistics.Builder builder = AdminAnalyticsUsageStatistics.newBuilder()
            .setTotalPlayerCount(intValue(usageStatistics.get("totalPlayerCount")));
        list(usageStatistics.get("topServersByUsers")).stream()
            .filter(Server.class::isInstance)
            .map(Server.class::cast)
            .forEach(server -> builder.addTopServersByUsers(AdminServerProtoMapper.toRecord(server)));
        listOfMaps(usageStatistics.get("serverActivity")).forEach(activity ->
            builder.addServerActivity(AdminAnalyticsServerActivity.newBuilder()
                .setDate(stringValue(activity.get("date")))
                .setActiveServers(longValue(activity.get("activeServers")))
                .build()));
        listOfMaps(usageStatistics.get("liveServers")).forEach(live ->
            builder.addLiveServers(toLiveServer(live)));
        listOfMaps(usageStatistics.get("playerActivity")).forEach(activity ->
            builder.addPlayerActivity(AdminAnalyticsPlayerActivity.newBuilder()
                .setDate(stringValue(activity.get("date")))
                .setPlayers(intValue(activity.get("players")))
                .build()));
        return builder.build();
    }

    private static AdminAnalyticsLiveServer toLiveServer(Map<String, Object> live) {
        AdminAnalyticsLiveServer.Builder builder = AdminAnalyticsLiveServer.newBuilder()
            .setServerId(stringValue(live.get("serverId")))
            .setServerName(stringValue(live.get("serverName")))
            .setPlayerCount(intValue(live.get("playerCount")));
        if (live.get("platform") != null) {
            builder.setPlatform(stringValue(live.get("platform")));
        }
        if (live.get("version") != null) {
            builder.setVersion(stringValue(live.get("version")));
        }
        if (live.get("pluginVersion") != null) {
            builder.setPluginVersion(stringValue(live.get("pluginVersion")));
        }
        return builder.build();
    }

    private static List<AdminAnalyticsNameValue> toNameValues(Object source) {
        return list(source).stream()
            .map(AdminAnalyticsProtoMapper::toNameValue)
            .toList();
    }

    private static AdminAnalyticsNameValue toNameValue(Object source) {
        if (source instanceof gg.modl.backend.database.mongo.repository.ServerMongoRepository.NameValueResult result) {
            return AdminAnalyticsNameValue.newBuilder()
                .setName(stringValue(result.name()))
                .setValue(result.value())
                .build();
        }
        Map<String, Object> map = map(source);
        return AdminAnalyticsNameValue.newBuilder()
            .setName(stringValue(map.get("name")))
            .setValue(intValue(map.get("value")))
            .build();
    }

    private static AdminAnalyticsDateServers toDateServers(Object source) {
        if (source instanceof gg.modl.backend.database.mongo.repository.ServerMongoRepository.DateServersResult result) {
            return AdminAnalyticsDateServers.newBuilder()
                .setDate(stringValue(result.date()))
                .setServers(result.servers())
                .build();
        }
        Map<String, Object> map = map(source);
        return AdminAnalyticsDateServers.newBuilder()
            .setDate(stringValue(map.get("date")))
            .setServers(intValue(map.get("servers")))
            .build();
    }

    private static Object readField(Object source, Map<String, Object> map, String field) {
        if (source instanceof gg.modl.backend.database.mongo.repository.ServerMongoRepository.DateValueResult result) {
            return field.equals("date") ? result.date() : result.value();
        }
        return map.get(field);
    }
}
