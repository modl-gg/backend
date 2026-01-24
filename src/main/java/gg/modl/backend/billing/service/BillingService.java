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
            updateServerField(server.getId(), "stripe_customer_id", customerId);
        }

        Session session = stripeService.createCheckoutSession(customerId, server.getCustomDomain());
        return new CheckoutSessionResponse(session.getId());
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
                .set("subscription_status", SubscriptionStatus.canceled);
        if (periodEndDate != null) {
            update.set("current_period_end", periodEndDate);
        }

        updateServer(server.getId(), update);

        return new CancelResponse(
                true,
                "Subscription cancelled successfully. Access will continue until the end of your current billing period.",
                periodEndDate
        );
    }

    public BillingStatusResponse getBillingStatus(Server server) {
        String currentStatus = server.getSubscriptionStatus() != null ? server.getSubscriptionStatus().name() : null;
        Date currentPeriodEnd = server.getCurrentPeriodEnd();
        Date currentPeriodStart = server.getCurrentPeriodStart();

        if (server.getStripeSubscriptionId() != null &&
                (currentStatus == null || "active".equals(currentStatus) || "canceled".equals(currentStatus))) {

            if (stripeService.isConfigured()) {
                try {
                    Subscription subscription = stripeService.retrieveSubscription(server.getStripeSubscriptionId());
                    String effectiveStatus = stripeService.getEffectiveStatus(subscription);
                    Date periodStartDate = stripeService.extractPeriodStart(subscription);
                    Date periodEndDate = stripeService.extractPeriodEnd(subscription);

                    boolean needsUpdate = !effectiveStatus.equals(currentStatus) ||
                            (periodEndDate != null && (currentPeriodEnd == null || Math.abs(currentPeriodEnd.getTime() - periodEndDate.getTime()) > 1000)) ||
                            (periodStartDate != null && (currentPeriodStart == null || Math.abs(currentPeriodStart.getTime() - periodStartDate.getTime()) > 1000));

                    if (needsUpdate) {
                        Update update = new Update()
                                .set("subscription_status", parseSubscriptionStatus(effectiveStatus));
                        if (periodStartDate != null) {
                            update.set("current_period_start", periodStartDate);
                        }
                        if (periodEndDate != null) {
                            update.set("current_period_end", periodEndDate);
                        }

                        updateServer(server.getId(), update);

                        currentStatus = effectiveStatus;
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
                currentStatus,
                currentPeriodStart,
                currentPeriodEnd
        );
    }

    public ResubscribeResponse resubscribe(Server server) throws StripeException {
        if (server.getSubscriptionStatus() != SubscriptionStatus.canceled) {
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
                .set("stripe_subscription_id", subscriptionResult.getId())
                .set("subscription_status", parseSubscriptionStatus(subscriptionResult.getStatus()))
                .set("plan", ServerPlan.premium);

        if (periodStartDate != null) {
            update.set("current_period_start", periodStartDate);
        }
        if (periodEndDate != null) {
            update.set("current_period_end", periodEndDate);
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
            return SubscriptionStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown subscription status from Stripe: {}, defaulting to inactive", status);
            return SubscriptionStatus.inactive;
        }
    }
}
