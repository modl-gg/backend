package gg.modl.backend.beta;

import gg.modl.backend.limits.ServerLimits;
import gg.modl.backend.server.data.Server;
import java.time.Instant;
import java.util.Date;

public record BetaTesterRecord(
    String id,
    String serverName,
    String customDomain,
    String adminEmail,
    String plan,
    String subscriptionStatus,
    Boolean betaTester,
    String provisioningStatus,
    Boolean emailVerified,
    Instant createdAt,
    Instant updatedAt,
    Instant betaTesterCreatedAt,
    String betaTesterCreatedBy,
    boolean apiKeySet,
    Usage usage,
    Limits limits
) {
    public record Usage(long storageUsedBytes, long userCount, long ticketCount, double cdnUsageGb, long aiRequestsUsed) {
    }

    public record Limits(long maxStaffSeats, long maxStorageBytes, long aiRequestLimit, double cdnLimitGb,
                         boolean customDomainAllowed, long maxUploadBytes) {
    }

    public static BetaTesterRecord from(Server server, ServerLimits limits) {
        return new BetaTesterRecord(
            server.getId(),
            server.getServerName(),
            server.getCustomDomain(),
            server.getAdminEmail(),
            server.getPlan() != null ? server.getPlan().getValue() : null,
            server.getSubscriptionStatus() != null ? server.getSubscriptionStatus().getValue() : null,
            server.getBetaTester(),
            server.getProvisioningStatus() != null ? server.getProvisioningStatus().getValue() : null,
            server.getEmailVerified(),
            toInstant(server.getCreatedAt()),
            toInstant(server.getUpdatedAt()),
            toInstant(server.getBetaTesterCreatedAt()),
            server.getBetaTesterCreatedBy(),
            server.getApiKey() != null && !server.getApiKey().isBlank(),
            new Usage(
                orZero(server.getStorageUsedBytes()),
                orZero(server.getUserCount()),
                orZero(server.getTicketCount()),
                orZero(server.getCdnUsageCurrentPeriod()),
                orZero(server.getAiRequestsCurrentPeriod())
            ),
            new Limits(
                limits.getMaxStaffSeats(),
                limits.getMaxStorageBytes(),
                limits.getAiRequestLimit(),
                limits.getCdnLimitGb(),
                limits.isCustomDomainAllowed(),
                limits.getMaxUploadBytes()
            )
        );
    }

    private static Instant toInstant(Date date) {
        return date != null ? date.toInstant() : null;
    }

    private static long orZero(Long value) {
        return value != null ? value : 0L;
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }
}
