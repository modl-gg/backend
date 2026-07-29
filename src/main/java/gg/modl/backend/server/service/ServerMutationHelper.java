package gg.modl.backend.server.service;

import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.database.mongo.repository.ServerAdminRepository;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerBillingUpdate;
import java.util.Date;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServerMutationHelper {

    private final ServerAdminRepository serverAdminRepository;
    private final ServerService serverService;

    public void mutate(Server server, Consumer<ServerBillingUpdate> mutator) {
        String beforeStripeCustomerId = server.getStripeCustomerId();
        String beforeStripeSubscriptionId = server.getStripeSubscriptionId();
        Object beforeSubscriptionStatus = server.getSubscriptionStatus();
        Object beforePlan = server.getPlan();
        Date beforeCurrentPeriodStart = server.getCurrentPeriodStart();
        Date beforeCurrentPeriodEnd = server.getCurrentPeriodEnd();
        Boolean beforeUsageBillingEnabled = server.getUsageBillingEnabled();
        Date beforeUsageBillingUpdatedAt = server.getUsageBillingUpdatedAt();
        Long beforeMaxStorageLimitBytes = server.getMaxStorageLimitBytes();
        Long beforeMaxAiOverageRequests = server.getMaxAiOverageRequests();
        Long beforeMigrationFileSizeLimit = server.getMigrationFileSizeLimit();

        mutator.accept(server);

        Update update = new Update();
        boolean changed = false;

        if (!Objects.equals(beforeStripeCustomerId, server.getStripeCustomerId())) {
            update.set(ServerFields.STRIPE_CUSTOMER_ID, server.getStripeCustomerId());
            changed = true;
        }
        if (!Objects.equals(beforeStripeSubscriptionId, server.getStripeSubscriptionId())) {
            update.set(ServerFields.STRIPE_SUBSCRIPTION_ID, server.getStripeSubscriptionId());
            changed = true;
        }
        if (!Objects.equals(beforeSubscriptionStatus, server.getSubscriptionStatus())) {
            update.set(ServerFields.SUBSCRIPTION_STATUS, server.getSubscriptionStatus());
            changed = true;
        }
        if (!Objects.equals(beforePlan, server.getPlan())) {
            update.set(ServerFields.PLAN, server.getPlan());
            changed = true;
        }
        if (!Objects.equals(beforeCurrentPeriodStart, server.getCurrentPeriodStart())) {
            update.set(ServerFields.CURRENT_PERIOD_START, server.getCurrentPeriodStart());
            changed = true;
        }
        if (!Objects.equals(beforeCurrentPeriodEnd, server.getCurrentPeriodEnd())) {
            update.set(ServerFields.CURRENT_PERIOD_END, server.getCurrentPeriodEnd());
            changed = true;
        }
        if (!Objects.equals(beforeUsageBillingEnabled, server.getUsageBillingEnabled())) {
            update.set(ServerFields.USAGE_BILLING_ENABLED, server.getUsageBillingEnabled());
            changed = true;
        }
        if (!Objects.equals(beforeUsageBillingUpdatedAt, server.getUsageBillingUpdatedAt())) {
            update.set(ServerFields.USAGE_BILLING_UPDATED_AT, server.getUsageBillingUpdatedAt());
            changed = true;
        }
        if (!Objects.equals(beforeMaxStorageLimitBytes, server.getMaxStorageLimitBytes())) {
            update.set(ServerFields.MAX_STORAGE_LIMIT_BYTES, server.getMaxStorageLimitBytes());
            changed = true;
        }
        if (!Objects.equals(beforeMaxAiOverageRequests, server.getMaxAiOverageRequests())) {
            update.set(ServerFields.MAX_AI_OVERAGE_REQUESTS, server.getMaxAiOverageRequests());
            changed = true;
        }
        if (!Objects.equals(beforeMigrationFileSizeLimit, server.getMigrationFileSizeLimit())) {
            update.set(ServerFields.MIGRATION_FILE_SIZE_LIMIT, server.getMigrationFileSizeLimit());
            changed = true;
        }

        if (!changed) {
            return;
        }

        update.set(ServerFields.UPDATED_AT, new Date());
        serverAdminRepository.applyFieldUpdate(server.getId(), update);
        serverService.evictAllServerCaches();
    }
}
