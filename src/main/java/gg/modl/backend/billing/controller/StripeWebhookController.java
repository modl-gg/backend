package gg.modl.backend.billing.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import gg.modl.backend.billing.config.StripeConfiguration;
import gg.modl.backend.billing.service.StripeService;
import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.WEBHOOKS_STRIPE)
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {
    private final StripeConfiguration config;
    private final StripeService stripeService;
    private final DynamicMongoTemplateProvider mongoProvider;
    private final UsageTrackingService usageTrackingService;

    @PostMapping
    public ResponseEntity<?> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        if (!stripeService.isConfigured()) {
            log.warn("Stripe not configured, ignoring webhook");
            return ResponseEntity.status(503).body("Stripe not configured");
        }

        if (config.getWebhookSecret() == null || config.getWebhookSecret().isBlank()) {
            log.error("STRIPE_WEBHOOK_SECRET not configured");
            return ResponseEntity.internalServerError().body("Webhook secret not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, config.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.error("Webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Webhook Error: " + e.getMessage());
        }

        try {
            processEvent(event);
            return ResponseEntity.ok(Map.of("received", true));
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.internalServerError().body("Webhook processing error");
        }
    }

    private void processEvent(Event event) {
        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event, globalDb);
            case "customer.subscription.created" -> handleSubscriptionCreated(event, globalDb);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event, globalDb);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event, globalDb);
            case "invoice.payment_failed" -> handlePaymentFailed(event, globalDb);
            case "invoice.payment_succeeded" -> handlePaymentSucceeded(event, globalDb);
            default -> log.debug("Unhandled event type: {}", event.getType());
        }
    }

    private void handleCheckoutCompleted(Event event, MongoTemplate globalDb) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof com.stripe.model.checkout.Session session)) {
            return;
        }

        if (session.getCustomer() != null && session.getSubscription() != null) {
            Server server = findServerByCustomerId(globalDb, session.getCustomer());
            if (server != null) {
                try {
                    Subscription subscription = stripeService.retrieveSubscription(session.getSubscription());
                    Date periodStartDate = stripeService.extractPeriodStart(subscription);
                    Date periodEndDate = stripeService.extractPeriodEnd(subscription);

                    Update update = new Update()
                            .set("stripe_subscription_id", session.getSubscription())
                            .set("subscription_status", SubscriptionStatus.active)
                            .set("plan", ServerPlan.premium);

                    if (periodStartDate != null) {
                        update.set("current_period_start", periodStartDate);
                    }
                    if (periodEndDate != null) {
                        update.set("current_period_end", periodEndDate);
                    }

                    updateServer(globalDb, server.getId(), update);
                } catch (Exception e) {
                    log.error("Error retrieving subscription details", e);
                }
            } else {
                log.warn("No server found for customer: {}", session.getCustomer());
            }
        }
    }

    private void handleSubscriptionCreated(Event event, MongoTemplate globalDb) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Subscription subscription)) {
            return;
        }

        Server server = findServerByCustomerId(globalDb, subscription.getCustomer());
        if (server != null) {
            Date periodStartDate = stripeService.extractPeriodStart(subscription);
            Date periodEndDate = stripeService.extractPeriodEnd(subscription);

            Update update = new Update()
                    .set("stripe_subscription_id", subscription.getId())
                    .set("subscription_status", parseSubscriptionStatus(subscription.getStatus()));

            ServerPlan plan = switch (subscription.getStatus()) {
                case "active", "trialing", "paused" -> ServerPlan.premium;
                case "past_due", "unpaid", "incomplete", "incomplete_expired" -> ServerPlan.free;
                default -> ServerPlan.premium;
            };
            update.set("plan", plan);

            if (periodStartDate != null) {
                update.set("current_period_start", periodStartDate);
            }
            if (periodEndDate != null) {
                update.set("current_period_end", periodEndDate);
            }

            updateServer(globalDb, server.getId(), update);
        }
    }

    private void handleSubscriptionUpdated(Event event, MongoTemplate globalDb) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Subscription subscription)) {
            return;
        }

        Server server = findServerBySubscriptionId(globalDb, subscription.getId());
        if (server != null) {
            Date periodStartDate = stripeService.extractPeriodStart(subscription);
            Date periodEndDate = stripeService.extractPeriodEnd(subscription);
            String effectiveStatus = stripeService.getEffectiveStatus(subscription);

            Update update = new Update()
                    .set("subscription_status", parseSubscriptionStatus(effectiveStatus));

            if ("active".equals(effectiveStatus)) {
                update.set("plan", ServerPlan.premium);
            } else if ("past_due".equals(effectiveStatus) || "unpaid".equals(effectiveStatus) ||
                    "incomplete".equals(effectiveStatus) || "incomplete_expired".equals(effectiveStatus)) {
                update.set("plan", ServerPlan.free);
            }

            if (periodStartDate != null) {
                update.set("current_period_start", periodStartDate);
            }
            if (periodEndDate != null) {
                update.set("current_period_end", periodEndDate);
            }

            updateServer(globalDb, server.getId(), update);
        } else {
            log.warn("No server found for subscription: {}", subscription.getId());
        }
    }

    private void handleSubscriptionDeleted(Event event, MongoTemplate globalDb) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Subscription subscription)) {
            return;
        }

        Server server = findServerBySubscriptionId(globalDb, subscription.getId());
        if (server != null) {
            Update update = new Update()
                    .set("subscription_status", SubscriptionStatus.canceled)
                    .set("plan", ServerPlan.free)
                    .unset("current_period_end");

            updateServer(globalDb, server.getId(), update);
            usageTrackingService.resetUsageCounters(server.getId());
        }
    }

    private void handlePaymentFailed(Event event, MongoTemplate globalDb) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Invoice invoice)) {
            return;
        }

        String customerId = invoice.getCustomer();
        if (customerId != null) {
            Server server = findServerByCustomerId(globalDb, customerId);
            if (server != null) {
                Update update = new Update()
                        .set("subscription_status", SubscriptionStatus.past_due)
                        .set("plan", ServerPlan.free);

                updateServer(globalDb, server.getId(), update);
            }
        }
    }

    private void handlePaymentSucceeded(Event event, MongoTemplate globalDb) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Invoice invoice)) {
            return;
        }

        String customerId = invoice.getCustomer();
        if (customerId != null) {
            Server server = findServerByCustomerId(globalDb, customerId);
            if (server != null && server.getSubscriptionStatus() == SubscriptionStatus.past_due) {
                Update update = new Update()
                        .set("subscription_status", SubscriptionStatus.active)
                        .set("plan", ServerPlan.premium);

                updateServer(globalDb, server.getId(), update);
            }
        }
    }

    private Server findServerByCustomerId(MongoTemplate globalDb, String customerId) {
        Query query = Query.query(Criteria.where("stripe_customer_id").is(customerId));
        return globalDb.findOne(query, Server.class, CollectionName.MODL_SERVERS);
    }

    private Server findServerBySubscriptionId(MongoTemplate globalDb, String subscriptionId) {
        Query query = Query.query(Criteria.where("stripe_subscription_id").is(subscriptionId));
        return globalDb.findOne(query, Server.class, CollectionName.MODL_SERVERS);
    }

    private void updateServer(MongoTemplate globalDb, String serverId, Update update) {
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
