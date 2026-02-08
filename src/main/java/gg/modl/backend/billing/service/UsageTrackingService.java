package gg.modl.backend.billing.service;

import gg.modl.backend.billing.dto.response.UsageBillingSettingsResponse;
import gg.modl.backend.billing.dto.response.UsageResponse;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.service.StorageQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageTrackingService {
    private static final double FREE_CDN_LIMIT_GB = 0.5; // 500 MB
    private static final double DEFAULT_PREMIUM_CDN_LIMIT_GB = 200.0;
    private static final int AI_LIMIT_REQUESTS = 10000;
    private static final double CDN_OVERAGE_RATE = 0.08;
    private static final double AI_OVERAGE_RATE = 0.01;

    private final DynamicMongoTemplateProvider mongoProvider;

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

        double cdnUsageGB = freshServer.getCdnUsageCurrentPeriod() != null ? freshServer.getCdnUsageCurrentPeriod() : 0.0;
        long aiRequestsUsed = freshServer.getAiRequestsCurrentPeriod() != null ? freshServer.getAiRequestsCurrentPeriod() : 0L;

        double cdnLimitGB = getCdnLimitGB(freshServer);
        double cdnOverageGB = Math.max(0, cdnUsageGB - cdnLimitGB);
        long aiOverageRequests = Math.max(0, aiRequestsUsed - AI_LIMIT_REQUESTS);

        double cdnOverageCost = cdnOverageGB * CDN_OVERAGE_RATE;
        double aiOverageCost = aiOverageRequests * AI_OVERAGE_RATE;
        double totalOverageCost = cdnOverageCost + aiOverageCost;

        boolean usageBillingEnabled = Boolean.TRUE.equals(freshServer.getUsageBillingEnabled());

        return new UsageResponse(
                new UsageResponse.Period(currentPeriodStart, currentPeriodEnd),
                new UsageResponse.UsageMetric(
                        cdnUsageGB,
                        cdnLimitGB,
                        cdnOverageGB,
                        CDN_OVERAGE_RATE,
                        cdnOverageCost,
                        Math.min(100, cdnLimitGB > 0 ? (cdnUsageGB / cdnLimitGB) * 100 : 0)
                ),
                new UsageResponse.UsageMetric(
                        aiRequestsUsed,
                        AI_LIMIT_REQUESTS,
                        aiOverageRequests,
                        AI_OVERAGE_RATE,
                        aiOverageCost,
                        Math.min(100, ((double) aiRequestsUsed / AI_LIMIT_REQUESTS) * 100)
                ),
                totalOverageCost,
                usageBillingEnabled
        );
    }

    public UsageBillingSettingsResponse updateUsageBillingSettings(Server server, boolean enabled) {
        if (enabled && (server.getStripeCustomerId() == null || server.getStripeCustomerId().isBlank())) {
            throw new IllegalStateException("No Stripe customer ID found. Please ensure you have an active subscription.");
        }

        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query query = Query.query(Criteria.where("_id").is(server.getId()));
        Update update = new Update()
                .set("usage_billing_enabled", enabled)
                .set("usage_billing_updated_at", new Date());

        globalDb.updateFirst(query, update, CollectionName.MODL_SERVERS);

        String message = enabled
                ? "Usage billing has been enabled. You will be charged for overages at the end of each billing period."
                : "Usage billing has been disabled. Overages will not be charged.";

        return new UsageBillingSettingsResponse(true, message, enabled);
    }

    public void incrementCdnUsage(String serverId, double additionalGB) {
        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query query = Query.query(Criteria.where("_id").is(serverId));
        Update update = new Update().inc("cdn_usage_current_period", additionalGB);
        globalDb.updateFirst(query, update, CollectionName.MODL_SERVERS);
    }

    public void incrementAiRequests(String serverId, long additionalRequests) {
        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query query = Query.query(Criteria.where("_id").is(serverId));
        Update update = new Update().inc("ai_requests_current_period", additionalRequests);
        globalDb.updateFirst(query, update, CollectionName.MODL_SERVERS);
    }

    public void resetUsageCounters(String serverId) {
        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query query = Query.query(Criteria.where("_id").is(serverId));
        Update update = new Update()
                .set("cdn_usage_current_period", 0.0)
                .set("ai_requests_current_period", 0L);
        globalDb.updateFirst(query, update, CollectionName.MODL_SERVERS);
    }

    public double getCdnLimitGB(Server server) {
        if (server.getPlan() == ServerPlan.premium) {
            if (server.getMaxStorageLimitBytes() != null && server.getMaxStorageLimitBytes() > 0) {
                double customGB = server.getMaxStorageLimitBytes() / (1024.0 * 1024 * 1024);
                double maxGB = StorageQuotaService.MAX_PREMIUM_BYTES / (1024.0 * 1024 * 1024);
                return Math.min(customGB, maxGB);
            }
            return DEFAULT_PREMIUM_CDN_LIMIT_GB;
        }
        return FREE_CDN_LIMIT_GB;
    }

    public void updateStorageLimit(Server server, long bytes) {
        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query query = Query.query(Criteria.where("_id").is(server.getId()));
        Update update = new Update().set("max_storage_limit_bytes", bytes);
        globalDb.updateFirst(query, update, CollectionName.MODL_SERVERS);
    }

    private Server getFreshServer(String serverId) {
        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query query = Query.query(Criteria.where("_id").is(serverId));
        return globalDb.findOne(query, Server.class, CollectionName.MODL_SERVERS);
    }
}
