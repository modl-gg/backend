package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.service.AdminServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.AdminServerBulkOperationData;
import gg.modl.proto.modl.v1.AdminServerBulkOperationResponse;
import gg.modl.proto.modl.v1.AdminServerDetailResponse;
import gg.modl.proto.modl.v1.AdminServerExportData;
import gg.modl.proto.modl.v1.AdminServerExportResponse;
import gg.modl.proto.modl.v1.AdminServerListData;
import gg.modl.proto.modl.v1.AdminServerListResponse;
import gg.modl.proto.modl.v1.AdminServerMutationResponse;
import gg.modl.proto.modl.v1.AdminServerPagination;
import gg.modl.proto.modl.v1.AdminServerRecord;
import gg.modl.proto.modl.v1.AdminServerSearchData;
import gg.modl.proto.modl.v1.AdminServerSearchResponse;
import gg.modl.proto.modl.v1.AdminServerStats;
import gg.modl.proto.modl.v1.AdminServerStatsResponse;
import gg.modl.proto.modl.v1.AdminServerUsageBatchData;
import gg.modl.proto.modl.v1.AdminServerUsageBatchResponse;
import gg.modl.proto.modl.v1.AdminServerUsageSummary;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

final class AdminServerProtoMapper {

    private AdminServerProtoMapper() {
    }

    static AdminServerRecord toRecord(Server server) {
        AdminServerRecord.Builder builder = AdminServerRecord.newBuilder()
            .setId(stringValue(server.getId()))
            .setServerName(stringValue(server.getServerName()))
            .setCustomDomain(stringValue(server.getCustomDomain()))
            .setAdminEmail(stringValue(server.getAdminEmail()))
            .setEmailVerified(server.getEmailVerified() != null && server.getEmailVerified())
            .setPlan(server.getPlan() != null ? server.getPlan().getValue() : "");

        if (server.getDatabaseName() != null) {
            builder.setDatabaseName(server.getDatabaseName());
        }
        if (server.getProvisioningStatus() != null) {
            builder.setProvisioningStatus(server.getProvisioningStatus().getValue());
        }
        if (server.getProvisioningNotes() != null) {
            builder.setProvisioningNotes(server.getProvisioningNotes());
        }
        if (server.getSubscriptionStatus() != null) {
            builder.setSubscriptionStatus(server.getSubscriptionStatus().getValue());
        }
        setTimestamp(server.getCurrentPeriodStart(), builder::setCurrentPeriodStart);
        setTimestamp(server.getCurrentPeriodEnd(), builder::setCurrentPeriodEnd);
        if (server.getStripeCustomerId() != null) {
            builder.setStripeCustomerId(server.getStripeCustomerId());
        }
        if (server.getStripeSubscriptionId() != null) {
            builder.setStripeSubscriptionId(server.getStripeSubscriptionId());
        }
        if (server.getCdnUsageCurrentPeriod() != null) {
            builder.setCdnUsageCurrentPeriod(server.getCdnUsageCurrentPeriod());
        }
        if (server.getAiRequestsCurrentPeriod() != null) {
            builder.setAiRequestsCurrentPeriod(server.getAiRequestsCurrentPeriod());
        }
        if (server.getUsageBillingEnabled() != null) {
            builder.setUsageBillingEnabled(server.getUsageBillingEnabled());
        }
        setTimestamp(server.getUsageBillingUpdatedAt(), builder::setUsageBillingUpdatedAt);
        if (server.getStorageUsedBytes() != null) {
            builder.setStorageUsedBytes(server.getStorageUsedBytes());
        }
        if (server.getMaxStorageLimitBytes() != null) {
            builder.setMaxStorageLimitBytes(server.getMaxStorageLimitBytes());
        }
        if (server.getMaxAiOverageRequests() != null) {
            builder.setMaxAiOverageRequests(server.getMaxAiOverageRequests());
        }
        if (server.getMigrationFileSizeLimit() != null) {
            builder.setMigrationFileSizeLimit(server.getMigrationFileSizeLimit());
        }
        if (server.getCustomDomainOverride() != null) {
            builder.setCustomDomainOverride(server.getCustomDomainOverride());
        }
        if (server.getCustomDomainStatus() != null) {
            builder.setCustomDomainStatus(server.getCustomDomainStatus().getValue());
        }
        setTimestamp(server.getCustomDomainLastChecked(), builder::setCustomDomainLastChecked);
        if (server.getCustomDomainError() != null) {
            builder.setCustomDomainError(server.getCustomDomainError());
        }
        if (server.getCustomDomainCloudflareId() != null) {
            builder.setCustomDomainCloudflareId(server.getCustomDomainCloudflareId());
        }
        if (server.getCustomDomainGrandfathered() != null) {
            builder.setCustomDomainGrandfathered(server.getCustomDomainGrandfathered());
        }
        if (server.getOnlinePlayerCount() != null) {
            builder.setOnlinePlayerCount(server.getOnlinePlayerCount());
        }
        if (server.getUserCount() != null) {
            builder.setUserCount(server.getUserCount());
        }
        if (server.getTicketCount() != null) {
            builder.setTicketCount(server.getTicketCount());
        }
        setTimestamp(server.getLastStatsUpdatedAt(), builder::setLastStatsUpdatedAt);
        setTimestamp(server.getLastActivityAt(), builder::setLastActivityAt);
        setTimestamp(server.getCreatedAt(), builder::setCreatedAt);
        setTimestamp(server.getUpdatedAt(), builder::setUpdatedAt);
        setTimestamp(server.getStaffPermissionsUpdatedAt(), builder::setStaffPermissionsUpdatedAt);
        setTimestamp(server.getPunishmentTypesUpdatedAt(), builder::setPunishmentTypesUpdatedAt);
        return builder.build();
    }

    static AdminServerListResponse toListResponse(List<Server> servers, int page, int limit, long total, int pages) {
        AdminServerListData.Builder data = AdminServerListData.newBuilder()
            .setPagination(AdminServerPagination.newBuilder()
                .setPage(page)
                .setLimit(limit)
                .setTotal(total)
                .setPages(pages)
                .build());
        servers.forEach(server -> data.addServers(toRecord(server)));
        return AdminServerListResponse.newBuilder()
            .setSuccess(true)
            .setData(data.build())
            .build();
    }

    static AdminServerDetailResponse toDetailResponse(Server server) {
        return AdminServerDetailResponse.newBuilder()
            .setSuccess(true)
            .setData(toRecord(server))
            .build();
    }

    static AdminServerMutationResponse toMutationResponse(Server server, String message) {
        AdminServerMutationResponse.Builder builder = AdminServerMutationResponse.newBuilder()
            .setSuccess(true);
        if (server != null) {
            builder.setData(toRecord(server));
        }
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    static AdminServerStatsResponse toStatsResponse(Map<String, Object> stats) {
        AdminServerStats.Builder data = AdminServerStats.newBuilder()
            .setTotalPlayers(longValue(stats.get("totalPlayers")))
            .setTotalTickets(longValue(stats.get("totalTickets")))
            .setTotalLogs(longValue(stats.get("totalLogs")))
            .setDatabaseSize(longValue(stats.get("databaseSize")));
        Object lastActivity = stats.get("lastActivity");
        if (lastActivity != null) {
            data.setLastActivity(toTimestamp(lastActivity));
        }
        return AdminServerStatsResponse.newBuilder()
            .setSuccess(true)
            .setData(data.build())
            .build();
    }

    static AdminServerUsageBatchResponse toUsageBatchResponse(Map<String, AdminServerService.UsageSummary> usage) {
        AdminServerUsageBatchData.Builder data = AdminServerUsageBatchData.newBuilder();
        usage.forEach((id, summary) -> data.putUsage(id, toUsageSummary(summary)));
        return AdminServerUsageBatchResponse.newBuilder()
            .setSuccess(true)
            .setData(data.build())
            .build();
    }

    static AdminServerBulkOperationResponse toBulkOperationResponse(String action, long affectedCount,
                                                                    List<String> serverIds, String message) {
        AdminServerBulkOperationData data = AdminServerBulkOperationData.newBuilder()
            .setAction(stringValue(action))
            .setAffectedCount(affectedCount)
            .addAllServerIds(serverIds)
            .build();
        AdminServerBulkOperationResponse.Builder builder = AdminServerBulkOperationResponse.newBuilder()
            .setSuccess(true)
            .setData(data);
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    static AdminServerSearchResponse toSearchResponse(List<Server> servers, long total) {
        AdminServerSearchData.Builder data = AdminServerSearchData.newBuilder().setTotal(total);
        servers.forEach(server -> data.addServers(toRecord(server)));
        return AdminServerSearchResponse.newBuilder()
            .setSuccess(true)
            .setData(data.build())
            .build();
    }

    static AdminServerExportResponse toExportResponse(List<Server> servers, Date exportedAt, String format, int count) {
        AdminServerExportData.Builder data = AdminServerExportData.newBuilder()
            .setExportedAt(toTimestamp(exportedAt))
            .setFormat(stringValue(format))
            .setCount(count);
        servers.forEach(server -> data.addServers(toRecord(server)));
        return AdminServerExportResponse.newBuilder()
            .setSuccess(true)
            .setData(data.build())
            .build();
    }

    private static AdminServerUsageSummary toUsageSummary(AdminServerService.UsageSummary summary) {
        AdminServerUsageSummary.Builder builder = AdminServerUsageSummary.newBuilder()
            .setUserCount(summary.userCount())
            .setTicketCount(summary.ticketCount())
            .setFromCache(summary.fromCache());
        if (summary.updatedAt() != null) {
            builder.setUpdatedAt(toTimestamp(summary.updatedAt()));
        }
        return builder.build();
    }

    private static void setTimestamp(Date date, java.util.function.Consumer<com.google.protobuf.Timestamp> setter) {
        if (date != null) {
            setter.accept(toTimestamp(date));
        }
    }
}
