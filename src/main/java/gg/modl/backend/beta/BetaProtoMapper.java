package gg.modl.backend.beta;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.doubleValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import com.google.protobuf.Timestamp;
import gg.modl.backend.beta.data.BetaAudit;
import gg.modl.backend.limits.ServerLimits;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.BetaAuditEntry;
import gg.modl.proto.modl.v1.BetaAuditResponse;
import gg.modl.proto.modl.v1.BetaTesterCreateRequest;
import gg.modl.proto.modl.v1.BetaTesterLimits;
import gg.modl.proto.modl.v1.BetaTesterListResponse;
import gg.modl.proto.modl.v1.BetaTesterPagination;
import gg.modl.proto.modl.v1.BetaTesterRecord;
import gg.modl.proto.modl.v1.BetaTesterResetAllResponse;
import gg.modl.proto.modl.v1.BetaTesterResetResponse;
import gg.modl.proto.modl.v1.BetaTesterResetResult;
import gg.modl.proto.modl.v1.BetaTesterUsage;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

final class BetaProtoMapper {

    private BetaProtoMapper() {
    }

    static BetaTesterCreation fromCreateRequest(BetaTesterCreateRequest request) {
        return new BetaTesterCreation(
            blankToNull(request.getServerName()),
            blankToNull(request.getCustomDomain()),
            blankToNull(request.getAdminEmail()));
    }

    static BetaTesterListResponse toListResponse(BetaTesterPage page) {
        BetaTesterListResponse.Builder builder = BetaTesterListResponse.newBuilder()
            .setPagination(BetaTesterPagination.newBuilder()
                .setPage(page.page())
                .setLimit(page.limit())
                .setTotal(page.total())
                .setPages(page.pages()));
        page.items().forEach(item -> builder.addItems(toRecord(item)));
        return builder.build();
    }

    static BetaTesterRecord toRecord(BetaTesterDetails details) {
        Server server = details.server();
        BetaTesterRecord.Builder builder = BetaTesterRecord.newBuilder()
            .setId(stringValue(server.getId()))
            .setServerName(stringValue(server.getServerName()))
            .setCustomDomain(stringValue(server.getCustomDomain()))
            .setAdminEmail(stringValue(server.getAdminEmail()))
            .setPlan(server.getPlan() != null ? server.getPlan().getValue() : "")
            .setSubscriptionStatus(server.getSubscriptionStatus() != null ? server.getSubscriptionStatus().getValue() : "")
            .setBetaTester(Boolean.TRUE.equals(server.getBetaTester()))
            .setProvisioningStatus(server.getProvisioningStatus() != null ? server.getProvisioningStatus().getValue() : "")
            .setEmailVerified(Boolean.TRUE.equals(server.getEmailVerified()))
            .setBetaTesterCreatedBy(stringValue(server.getBetaTesterCreatedBy()))
            .setApiKeySet(server.getApiKey() != null && !server.getApiKey().isBlank())
            .setUsage(toUsage(server))
            .setLimits(toLimits(details.limits()));
        setTimestamp(server.getCreatedAt(), builder::setCreatedAt);
        setTimestamp(server.getUpdatedAt(), builder::setUpdatedAt);
        setTimestamp(server.getBetaTesterCreatedAt(), builder::setBetaTesterCreatedAt);
        return builder.build();
    }

    static BetaTesterResetResponse toResetResponse(BetaResetResponse result) {
        return BetaTesterResetResponse.newBuilder()
            .setServerId(stringValue(result.serverId()))
            .addAllClearedCollections(result.clearedCollections())
            .build();
    }

    static BetaTesterResetAllResponse toResetAllResponse(List<ResetResult> results) {
        BetaTesterResetAllResponse.Builder builder = BetaTesterResetAllResponse.newBuilder();
        results.forEach(result -> builder.addResults(toResetResult(result)));
        return builder.build();
    }

    static BetaAuditResponse toAuditResponse(List<BetaAudit> entries) {
        BetaAuditResponse.Builder builder = BetaAuditResponse.newBuilder();
        entries.forEach(entry -> builder.addEntries(toAuditEntry(entry)));
        return builder.build();
    }

    private static BetaTesterUsage toUsage(Server server) {
        return BetaTesterUsage.newBuilder()
            .setStorageUsedBytes(longValue(server.getStorageUsedBytes()))
            .setUserCount(longValue(server.getUserCount()))
            .setTicketCount(longValue(server.getTicketCount()))
            .setCdnUsageGb(doubleValue(server.getCdnUsageCurrentPeriod()))
            .setAiRequestsUsed(longValue(server.getAiRequestsCurrentPeriod()))
            .build();
    }

    private static BetaTesterLimits toLimits(ServerLimits limits) {
        return BetaTesterLimits.newBuilder()
            .setMaxStaffSeats(limits.getMaxStaffSeats())
            .setMaxStorageBytes(limits.getMaxStorageBytes())
            .setAiRequestLimit(limits.getAiRequestLimit())
            .setCdnLimitGb(limits.getCdnLimitGb())
            .setCustomDomainAllowed(limits.isCustomDomainAllowed())
            .setMaxUploadBytes(limits.getMaxUploadBytes())
            .build();
    }

    private static BetaTesterResetResult toResetResult(ResetResult result) {
        BetaTesterResetResult.Builder builder = BetaTesterResetResult.newBuilder()
            .setServerId(stringValue(result.serverId()))
            .setServerName(stringValue(result.serverName()))
            .setSuccess(result.success());
        if (result.message() != null) {
            builder.setMessage(result.message());
        }
        return builder.build();
    }

    private static BetaAuditEntry toAuditEntry(BetaAudit audit) {
        BetaAuditEntry.Builder builder = BetaAuditEntry.newBuilder()
            .setAction(stringValue(audit.getAction()))
            .setAdminEmail(stringValue(audit.getAdminEmail()));
        setTimestamp(audit.getTimestamp(), builder::setTimestamp);
        if (audit.getDetails() != null) {
            builder.setDetails(audit.getDetails());
        }
        return builder.build();
    }

    private static void setTimestamp(Date date, Consumer<Timestamp> setter) {
        if (date != null) {
            setter.accept(toTimestamp(date));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
