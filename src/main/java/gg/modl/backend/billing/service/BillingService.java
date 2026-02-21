package gg.modl.backend.billing.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import gg.modl.backend.billing.dto.response.*;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {
    private final StripeService stripeService;
    private final DynamicMongoTemplateProvider mongoProvider;

    public CheckoutSessionResponse createCheckoutSession(Server server) throws StripeException {
        String customerId = server.getStripeCustomerId();

        if (customerId == null || customerId.isBlank()) {
            customerId = stripeService.createCustomer(server);
            updateServerField(server.getId(), "stripeCustomerId", customerId);
        }

        Session session = stripeService.createCheckoutSession(customerId, server.getCustomDomain());
        return new CheckoutSessionResponse(session.getId(), session.getUrl());
    }

    public PortalSessionResponse createPortalSession(Server server) throws StripeException {
        if (server.getStripeCustomerId() == null || server.getStripeCustomerId().isBlank()) {
            throw new IllegalStateException("Customer ID not found for server");
        }

        com.stripe.model.billingportal.Session session = stripeService.createPortalSession(server.getStripeCustomerId(), server.getCustomDomain());
        return new PortalSessionResponse(session.getUrl());
    }

    public CancelResponse cancelSubscription(Server server) throws StripeException {
        if (server.getStripeSubscriptionId() == null || server.getStripeSubscriptionId().isBlank()) {
            throw new IllegalStateException("No active subscription found to cancel");
        }

        Subscription canceledSubscription = stripeService.cancelSubscription(server.getStripeSubscriptionId());

        Date periodEndDate = server.getCurrentPeriodEnd();
        if (periodEndDate == null) {
            periodEndDate = stripeService.extractPeriodEnd(canceledSubscription);
        }

        Update update = new Update()
                .set("subscriptionStatus", SubscriptionStatus.CANCELED);
        if (periodEndDate != null) {
            update.set("currentPeriodEnd", periodEndDate);
        }

        updateServer(server.getId(), update);

        return new CancelResponse(
                true,
                "Subscription cancelled successfully. Access will continue until the end of your current billing period.",
                periodEndDate
        );
    }

    public BillingStatusResponse getBillingStatus(Server server) {
        SubscriptionStatus currentStatus = server.getSubscriptionStatus();
        Date currentPeriodEnd = server.getCurrentPeriodEnd();
        Date currentPeriodStart = server.getCurrentPeriodStart();

        if (server.getStripeSubscriptionId() != null &&
                (currentStatus == null || currentStatus == SubscriptionStatus.ACTIVE || currentStatus == SubscriptionStatus.CANCELED)) {

            if (stripeService.isConfigured()) {
                try {
                    Subscription subscription = stripeService.retrieveSubscription(server.getStripeSubscriptionId());
                    String effectiveStatus = stripeService.getEffectiveStatus(subscription);
                    SubscriptionStatus effectiveSubscriptionStatus = parseSubscriptionStatus(effectiveStatus);
                    Date periodStartDate = stripeService.extractPeriodStart(subscription);
                    Date periodEndDate = stripeService.extractPeriodEnd(subscription);

                    boolean needsUpdate = effectiveSubscriptionStatus != currentStatus ||
                            (periodEndDate != null && (currentPeriodEnd == null || Math.abs(currentPeriodEnd.getTime() - periodEndDate.getTime()) > 1000)) ||
                            (periodStartDate != null && (currentPeriodStart == null || Math.abs(currentPeriodStart.getTime() - periodStartDate.getTime()) > 1000));

                    if (needsUpdate) {
                        Update update = new Update()
                                .set("subscriptionStatus", effectiveSubscriptionStatus);
                        if (periodStartDate != null) {
                            update.set("currentPeriodStart", periodStartDate);
                        }
                        if (periodEndDate != null) {
                            update.set("currentPeriodEnd", periodEndDate);
                        }

                        updateServer(server.getId(), update);

                        currentStatus = effectiveSubscriptionStatus;
                        if (periodStartDate != null) {
                            currentPeriodStart = periodStartDate;
                        }
                        if (periodEndDate != null) {
                            currentPeriodEnd = periodEndDate;
                        }
                    }
                } catch (StripeException e) {
                    log.error("Error fetching subscription from Stripe", e);
                }
            }
        }

        return new BillingStatusResponse(
                server.getPlan() != null ? server.getPlan().name() : null,
                currentStatus != null ? currentStatus.name() : null,
                currentPeriodStart,
                currentPeriodEnd,
                server.getMaxStorageLimitBytes(),
                server.getMaxAiOverageRequests()
        );
    }

    public ResubscribeResponse resubscribe(Server server) throws StripeException {
        if (server.getSubscriptionStatus() != SubscriptionStatus.CANCELED) {
            throw new IllegalStateException("No cancelled subscription found to reactivate.");
        }

        Subscription subscriptionResult;

        if (server.getStripeSubscriptionId() != null) {
            try {
                Subscription existingSubscription = stripeService.retrieveSubscription(server.getStripeSubscriptionId());

                if ("active".equals(existingSubscription.getStatus()) &&
                        Boolean.TRUE.equals(existingSubscription.getCancelAtPeriodEnd())) {
                    subscriptionResult = stripeService.reactivateSubscription(server.getStripeSubscriptionId());
                } else if ("canceled".equals(existingSubscription.getStatus())) {
                    subscriptionResult = createNewSubscription(server);
                } else {
                    throw new IllegalStateException("Subscription is not in a cancelled state that can be reactivated.");
                }
            } catch (StripeException e) {
                if ("resource_missing".equals(e.getCode())) {
                    subscriptionResult = createNewSubscription(server);
                } else {
                    throw e;
                }
            }
        } else {
            subscriptionResult = createNewSubscription(server);
        }

        Date periodStartDate = stripeService.extractPeriodStart(subscriptionResult);
        Date periodEndDate = stripeService.extractPeriodEnd(subscriptionResult);

        Update update = new Update()
                .set("stripeSubscriptionId", subscriptionResult.getId())
                .set("subscriptionStatus", parseSubscriptionStatus(subscriptionResult.getStatus()))
                .set("plan", ServerPlan.PREMIUM);

        if (periodStartDate != null) {
            update.set("currentPeriodStart", periodStartDate);
        }
        if (periodEndDate != null) {
            update.set("currentPeriodEnd", periodEndDate);
        }

        updateServer(server.getId(), update);

        return new ResubscribeResponse(
                true,
                "Subscription reactivated successfully! Your premium features are now active.",
                new ResubscribeResponse.SubscriptionInfo(
                        subscriptionResult.getId(),
                        subscriptionResult.getStatus(),
                        periodEndDate
                )
        );
    }

    private Subscription createNewSubscription(Server server) throws StripeException {
        if (server.getStripeCustomerId() == null || server.getStripeCustomerId().isBlank()) {
            throw new IllegalStateException("No Stripe customer ID found. Cannot create subscription.");
        }
        return stripeService.createSubscription(server.getStripeCustomerId());
    }

    private void updateServerField(String serverId, String field, Object value) {
        Update update = new Update().set(field, value);
        updateServer(serverId, update);
    }

    private void updateServer(String serverId, Update update) {
        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query query = Query.query(Criteria.where("_id").is(serverId));
        globalDb.updateFirst(query, update, CollectionName.MODL_SERVERS);
    }

    private SubscriptionStatus parseSubscriptionStatus(String status) {
        try {
            return SubscriptionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown subscription status from Stripe: {}, defaulting to inactive", status);
            return SubscriptionStatus.INACTIVE;
        }
    }
}
