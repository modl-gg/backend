package gg.modl.backend.billing.service;

import gg.modl.backend.billing.dto.response.UsageBillingSettingsResponse;
import gg.modl.backend.billing.dto.response.UsageResponse;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.limits.ServerLimitPolicy;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.service.StorageQuotaService;
import gg.modl.backend.server.service.ServerMutationHelper;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageTrackingService {
    private final ServerMongoRepository serverRepository;
    private final ServerMutationHelper serverMutationHelper;
    private final ServerLimitPolicy serverLimitPolicy;
    public static final long AI_BASE_LIMIT_REQUESTS = 1000L;
    private static final double AI_OVERAGE_RATE = 0.02;

    public UsageResponse getUsage(Server server) {
        Server freshServer = getFreshServer(server.getId());
        if (freshServer == null) {
            throw new IllegalStateException("Server not found in database.");
        }

        Date currentPeriodStart = freshServer.getCurrentPeriodStart();
        if (currentPeriodStart == null) {
            currentPeriodStart = new Date(System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000));
        }

        Date currentPeriodEnd = freshServer.getCurrentPeriodEnd();
        if (currentPeriodEnd == null) {
            currentPeriodEnd = new Date(System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000));
        }

        long aiRequestsUsed = freshServer.getAiRequestsCurrentPeriod() != null ? freshServer.getAiRequestsCurrentPeriod() : 0L;
        boolean usageBillingEnabled = Boolean.TRUE.equals(freshServer.getUsageBillingEnabled());

        long aiLimitRequests = getAiRequestLimit(freshServer);
        long aiOverageRequests = Math.max(0, aiRequestsUsed - getAiBaseLimitRequests());
        double aiOverageCost = usageBillingEnabled ? aiOverageRequests * AI_OVERAGE_RATE : 0.0;

        return new UsageResponse(
            new UsageResponse.Period(currentPeriodStart, currentPeriodEnd),
            new UsageResponse.UsageMetric(
                aiRequestsUsed,
                aiLimitRequests,
                aiOverageRequests,
                AI_OVERAGE_RATE,
                aiOverageCost,
                Math.min(100, aiLimitRequests > 0 ? ((double) aiRequestsUsed / aiLimitRequests) * 100 : 0)
            ),
            aiOverageCost,
            usageBillingEnabled
        );
    }

    public long getAiRequestLimit(Server server) {
        return serverLimitPolicy.resolve(server).getAiRequestLimit();
    }

    public long getAiBaseLimitRequests() {
        return AI_BASE_LIMIT_REQUESTS;
    }

    private Server getFreshServer(String serverId) {
        return serverRepository.findById(serverId).orElse(null);
    }

    public UsageBillingSettingsResponse updateUsageBillingSettings(Server server, boolean enabled) {
        if (enabled && (server.getStripeCustomerId() == null || server.getStripeCustomerId().isBlank())) {
            throw new IllegalStateException("No Stripe customer ID found. Please ensure you have an active subscription.");
        }

        serverMutationHelper.mutate(server, current -> {
            current.setUsageBillingEnabled(enabled);
            current.setUsageBillingUpdatedAt(new Date());
        });

        String message = enabled
                         ? "Usage billing has been enabled. You will be charged for overages at the end of each billing period."
                         : "Usage billing has been disabled. Overages will not be charged.";

        return new UsageBillingSettingsResponse(true, message, enabled);
    }

    public void incrementAiRequests(String serverId, long additionalRequests) {
        serverRepository.incrementAiRequests(serverId, additionalRequests);
    }

    public void resetUsageCounters(String serverId) {
        serverRepository.resetUsageCounters(serverId);
    }

    public void updateStorageLimit(Server server, long bytes) {
        if (server.getPlan() != ServerPlan.PREMIUM) {
            throw new ValidationException("Storage limit configuration is only available for premium servers");
        }
        if (bytes <= 0) {
            throw new ValidationException("Storage limit must be positive");
        }
        validatePremiumStorageBytes(bytes);
        serverMutationHelper.mutate(server, current -> current.setMaxStorageLimitBytes(bytes));
    }

    public long updateOverageLimits(Server server, long maxStorageOverageGb, long maxAiOverageRequests) {
        if (server.getPlan() != ServerPlan.PREMIUM) {
            throw new ValidationException("Overage limits configuration is only available for premium servers");
        }
        if (maxStorageOverageGb < 0 || maxAiOverageRequests < 0) {
            throw new ValidationException("Overage limits cannot be negative");
        }
        if (maxStorageOverageGb > StorageQuotaService.MAX_STORAGE_OVERAGE_BYTES / (1024L * 1024 * 1024)) {
            throw new ValidationException("Storage overage cannot exceed 2000 GB. Please contact support for higher limits.");
        }
        if (maxAiOverageRequests > StorageQuotaService.MAX_AI_OVERAGE_REQUESTS) {
            throw new ValidationException("AI request overage cannot exceed 5000 requests. Please contact support for higher limits.");
        }

        long maxStorageLimitBytes = StorageQuotaService.PREMIUM_BASE_BYTES + maxStorageOverageGb * (1024L * 1024 * 1024);
        validatePremiumStorageBytes(maxStorageLimitBytes);

        serverMutationHelper.mutate(server, current -> {
            current.setMaxStorageLimitBytes(maxStorageLimitBytes);
            current.setMaxAiOverageRequests(maxAiOverageRequests);
        });
        return maxStorageLimitBytes;
    }

    private void validatePremiumStorageBytes(long bytes) {
        if (bytes > StorageQuotaService.MAX_PREMIUM_BYTES) {
            throw new ValidationException("Storage limit cannot exceed 2200 GB. Please contact support for higher limits.");
        }
    }

}
