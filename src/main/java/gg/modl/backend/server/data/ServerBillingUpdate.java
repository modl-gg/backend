package gg.modl.backend.server.data;

import java.util.Date;

public interface ServerBillingUpdate {
    String getStripeSubscriptionId();

    void setStripeCustomerId(String stripeCustomerId);

    void setStripeSubscriptionId(String stripeSubscriptionId);

    void setSubscriptionStatus(SubscriptionStatus subscriptionStatus);

    void setPlan(ServerPlan plan);

    void setCurrentPeriodStart(Date currentPeriodStart);

    void setCurrentPeriodEnd(Date currentPeriodEnd);

    void setUsageBillingEnabled(Boolean usageBillingEnabled);

    void setUsageBillingUpdatedAt(Date usageBillingUpdatedAt);

    void setMaxStorageLimitBytes(Long maxStorageLimitBytes);

    void setMaxAiOverageRequests(Long maxAiOverageRequests);

    void setMigrationFileSizeLimit(Long migrationFileSizeLimit);
}
