package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.SystemLog;
import gg.modl.backend.admin.dto.request.CreateSystemLogRequest;
import gg.modl.backend.admin.dto.request.ResolveLogRequest;
import gg.modl.proto.modl.v1.AdminMonitoringDashboardData;
import gg.modl.proto.modl.v1.AdminMonitoringDashboardResponse;
import gg.modl.proto.modl.v1.AdminMonitoringDeleteLogsResponse;
import gg.modl.proto.modl.v1.AdminMonitoringDeletedCount;
import gg.modl.proto.modl.v1.AdminMonitoringHealthCheck;
import gg.modl.proto.modl.v1.AdminMonitoringHealthData;
import gg.modl.proto.modl.v1.AdminMonitoringHealthResponse;
import gg.modl.proto.modl.v1.AdminMonitoringLogFilters;
import gg.modl.proto.modl.v1.AdminMonitoringLogMetrics;
import gg.modl.proto.modl.v1.AdminMonitoringLogWindow;
import gg.modl.proto.modl.v1.AdminMonitoringLogsData;
import gg.modl.proto.modl.v1.AdminMonitoringLogsResponse;
import gg.modl.proto.modl.v1.AdminMonitoringPagination;
import gg.modl.proto.modl.v1.AdminMonitoringPm2RestartResponse;
import gg.modl.proto.modl.v1.AdminMonitoringPm2Status;
import gg.modl.proto.modl.v1.AdminMonitoringPm2StatusResponse;
import gg.modl.proto.modl.v1.AdminMonitoringPm2ToggleData;
import gg.modl.proto.modl.v1.AdminMonitoringPm2ToggleResponse;
import gg.modl.proto.modl.v1.AdminMonitoringServerMetrics;
import gg.modl.proto.modl.v1.AdminMonitoringSourcesData;
import gg.modl.proto.modl.v1.AdminMonitoringSourcesResponse;
import gg.modl.proto.modl.v1.AdminMonitoringSystemHealthSummary;
import gg.modl.proto.modl.v1.AdminMonitoringSystemLogMutationResponse;
import gg.modl.proto.modl.v1.AdminMonitoringUnresolvedLogs;
import gg.modl.proto.modl.v1.SystemLogResponse;

import java.util.Map;
import java.util.function.Consumer;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.doubleValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.list;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.listOfMaps;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.map;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toStruct;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

final class AdminMonitoringProtoMapper {

    private AdminMonitoringProtoMapper() {
    }

    static CreateSystemLogRequest fromCreateLog(gg.modl.proto.modl.v1.CreateSystemLogRequest request) {
        Map<String, Object> metadata = request.hasMetadata()
            ? gg.modl.backend.infrastructure.proto.ProtoMapperSupport.structToMap(request.getMetadata())
            : null;
        return new CreateSystemLogRequest(
            request.getLevel(),
            request.getMessage(),
            request.getSource(),
            request.getCategory(),
            request.getServerId(),
            metadata
        );
    }

    static ResolveLogRequest fromResolveLog(gg.modl.proto.modl.v1.ResolveLogRequest request) {
        return new ResolveLogRequest(request.hasResolvedBy() ? request.getResolvedBy() : null);
    }

    static AdminMonitoringDashboardResponse toDashboardResponse(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        Map<String, Object> servers = map(data.get("servers"));
        Map<String, Object> logs = map(data.get("logs"));
        Map<String, Object> last24h = map(logs.get("last24h"));
        Map<String, Object> unresolved = map(logs.get("unresolved"));
        Map<String, Object> systemHealth = map(data.get("systemHealth"));

        AdminMonitoringDashboardData.Builder builder = AdminMonitoringDashboardData.newBuilder()
            .setServers(AdminMonitoringServerMetrics.newBuilder()
                .setTotal(longValue(servers.get("total")))
                .setActive(longValue(servers.get("active")))
                .setPending(longValue(servers.get("pending")))
                .setFailed(longValue(servers.get("failed")))
                .setRecentRegistrations(longValue(servers.get("recentRegistrations")))
                .setConcurrentServers(longValue(servers.get("concurrentServers")))
                .setConcurrentPlayers(longValue(servers.get("concurrentPlayers")))
                .build())
            .setLogs(AdminMonitoringLogMetrics.newBuilder()
                .setLast24H(AdminMonitoringLogWindow.newBuilder()
                    .setTotal(longValue(last24h.get("total")))
                    .setCritical(longValue(last24h.get("critical")))
                    .setError(longValue(last24h.get("error")))
                    .setWarning(longValue(last24h.get("warning")))
                    .build())
                .setUnresolved(AdminMonitoringUnresolvedLogs.newBuilder()
                    .setCritical(longValue(unresolved.get("critical")))
                    .setError(longValue(unresolved.get("error")))
                    .build())
                .build())
            .setSystemHealth(AdminMonitoringSystemHealthSummary.newBuilder()
                .setScore(doubleValue(systemHealth.get("score")))
                .setStatus(stringValue(systemHealth.get("status")))
                .build());
        listOfMaps(data.get("trends")).forEach(trend -> builder.addTrends(toStruct(trend)));
        Object lastUpdated = data.get("lastUpdated");
        if (lastUpdated != null) {
            builder.setLastUpdated(toTimestamp(lastUpdated));
        }
        return AdminMonitoringDashboardResponse.newBuilder()
            .setSuccess(true)
            .setData(builder.build())
            .build();
    }

    static AdminMonitoringLogsResponse toLogsResponse(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        Map<String, Object> pagination = map(data.get("pagination"));
        Map<String, Object> filters = map(data.get("filters"));

        AdminMonitoringLogsData.Builder dataBuilder = AdminMonitoringLogsData.newBuilder()
            .setPagination(AdminMonitoringPagination.newBuilder()
                .setPage((int) longValue(pagination.get("page")))
                .setLimit((int) longValue(pagination.get("limit")))
                .setTotal(longValue(pagination.get("total")))
                .setPages((int) longValue(pagination.get("pages")))
                .build())
            .setFilters(toLogFilters(filters));
        list(data.get("logs")).stream()
            .filter(SystemLog.class::isInstance)
            .map(SystemLog.class::cast)
            .forEach(systemLog -> dataBuilder.addLogs(toSystemLogResponse(systemLog)));
        return AdminMonitoringLogsResponse.newBuilder()
            .setSuccess(true)
            .setData(dataBuilder.build())
            .build();
    }

    static AdminMonitoringSystemLogMutationResponse toSystemLogMutationResponse(SystemLog systemLog, String message) {
        AdminMonitoringSystemLogMutationResponse.Builder builder = AdminMonitoringSystemLogMutationResponse.newBuilder()
            .setSuccess(true)
            .setData(toSystemLogResponse(systemLog));
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    static AdminMonitoringSourcesResponse toSourcesResponse(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        AdminMonitoringSourcesData.Builder dataBuilder = AdminMonitoringSourcesData.newBuilder();
        list(data.get("sources")).forEach(source -> dataBuilder.addSources(stringValue(source)));
        list(data.get("categories")).forEach(category -> dataBuilder.addCategories(stringValue(category)));
        return AdminMonitoringSourcesResponse.newBuilder()
            .setSuccess(true)
            .setData(dataBuilder.build())
            .build();
    }

    static AdminMonitoringHealthResponse toHealthResponse(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        AdminMonitoringHealthData.Builder dataBuilder = AdminMonitoringHealthData.newBuilder()
            .setStatus(stringValue(data.get("status")));
        listOfMaps(data.get("checks")).forEach(check -> dataBuilder.addChecks(toHealthCheck(check)));
        Object timestamp = data.get("timestamp");
        if (timestamp != null) {
            dataBuilder.setTimestamp(toTimestamp(timestamp));
        }
        return AdminMonitoringHealthResponse.newBuilder()
            .setSuccess(true)
            .setData(dataBuilder.build())
            .build();
    }

    static AdminMonitoringDeleteLogsResponse toDeleteLogsResponse(long deletedCount, String message) {
        AdminMonitoringDeleteLogsResponse.Builder builder = AdminMonitoringDeleteLogsResponse.newBuilder()
            .setSuccess(true)
            .setData(AdminMonitoringDeletedCount.newBuilder().setDeletedCount(deletedCount).build());
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    static AdminMonitoringPm2StatusResponse toPm2StatusResponse() {
        return AdminMonitoringPm2StatusResponse.newBuilder()
            .setSuccess(true)
            .setData(AdminMonitoringPm2Status.newBuilder()
                .setIsEnabled(false)
                .setIsStreaming(false)
                .setReconnectAttempts(0)
                .setRecentLogsCount(0)
                .build())
            .build();
    }

    static AdminMonitoringPm2RestartResponse toPm2RestartResponse(String message) {
        AdminMonitoringPm2RestartResponse.Builder builder = AdminMonitoringPm2RestartResponse.newBuilder()
            .setSuccess(true);
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    static AdminMonitoringPm2ToggleResponse toPm2ToggleResponse(boolean enabled, String message) {
        AdminMonitoringPm2ToggleResponse.Builder builder = AdminMonitoringPm2ToggleResponse.newBuilder()
            .setSuccess(true)
            .setData(AdminMonitoringPm2ToggleData.newBuilder()
                .setIsEnabled(enabled)
                .setIsStreaming(enabled)
                .build());
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    private static AdminMonitoringLogFilters toLogFilters(Map<String, Object> filters) {
        AdminMonitoringLogFilters.Builder builder = AdminMonitoringLogFilters.newBuilder();
        setFilter(filters.get("level"), builder::setLevel);
        setFilter(filters.get("source"), builder::setSource);
        setFilter(filters.get("serverId"), builder::setServerId);
        setFilter(filters.get("category"), builder::setCategory);
        setFilter(filters.get("resolved"), builder::setResolved);
        setFilter(filters.get("search"), builder::setSearch);
        return builder.build();
    }

    private static void setFilter(Object value, Consumer<String> setter) {
        if (value != null) {
            setter.accept(stringValue(value));
        }
    }

    private static AdminMonitoringHealthCheck toHealthCheck(Map<String, Object> check) {
        AdminMonitoringHealthCheck.Builder builder = AdminMonitoringHealthCheck.newBuilder()
            .setName(stringValue(check.get("name")))
            .setStatus(stringValue(check.get("status")));
        if (check.get("message") != null) {
            builder.setMessage(stringValue(check.get("message")));
        }
        if (check.get("responseTime") != null) {
            builder.setResponseTime(longValue(check.get("responseTime")));
        }
        if (check.get("error") != null) {
            builder.setError(stringValue(check.get("error")));
        }
        if (check.get("count") != null) {
            builder.setCount(longValue(check.get("count")));
        }
        return builder.build();
    }

    private static SystemLogResponse toSystemLogResponse(SystemLog systemLog) {
        SystemLogResponse.Builder builder = SystemLogResponse.newBuilder()
            .setId(stringValue(systemLog.getId()))
            .setMessage(stringValue(systemLog.getMessage()))
            .setLevel(stringValue(systemLog.getLevel()))
            .setSource(stringValue(systemLog.getSource()))
            .setResolved(systemLog.isResolved());
        if (systemLog.getTimestamp() != null) {
            builder.setTimestamp(toTimestamp(systemLog.getTimestamp()));
        }
        if (systemLog.getCategory() != null) {
            builder.setCategory(systemLog.getCategory());
        }
        if (systemLog.getServerId() != null) {
            builder.setServerId(systemLog.getServerId());
        }
        if (systemLog.getMetadata() != null) {
            builder.setMetadata(toStruct(systemLog.getMetadata()));
        }
        if (systemLog.getResolvedBy() != null) {
            builder.setResolvedBy(systemLog.getResolvedBy());
        }
        if (systemLog.getResolvedAt() != null) {
            builder.setResolvedAt(toTimestamp(systemLog.getResolvedAt()));
        }
        return builder.build();
    }
}
