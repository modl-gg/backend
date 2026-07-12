package gg.modl.backend.billing.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import gg.modl.backend.database.mongo.repository.StripeWebhookEventMongoRepository;
import gg.modl.backend.database.mongo.repository.ServerLookupRepository;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerBillingUpdate;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import gg.modl.backend.server.service.ServerMutationHelper;
import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {
    private final StripeService stripeService;
    private final ServerLookupRepository serverLookupRepository;
    private final UsageTrackingService usageTrackingService;
    private final ServerMutationHelper serverMutationHelper;
    private final StripeWebhookEventMongoRepository webhookEventRepository;

    public void processEvent(Event event) {
        if (!webhookEventRepository.markProcessing(event.getId(), event.getType(), new Date())) {
            log.info("Ignoring duplicate Stripe webhook event {}", event.getId());
            return;
        }
        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> handleCheckoutCompleted(event);
                case "customer.subscription.created" -> handleSubscriptionCreated(event);
                case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
                case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
                case "invoice.payment_failed" -> handlePaymentFailed(event);
                case "invoice.payment_succeeded" -> handlePaymentSucceeded(event);
                default -> log.debug("Unhandled event type: {}", event.getType());
            }
            webhookEventRepository.markProcessed(event.getId(), new Date());
        } catch (RuntimeException exception) {
            webhookEventRepository.markFailed(event.getId(), new Date(), exception.getMessage());
            throw exception;
        }
    }

    private <T extends StripeObject> Optional<T> objectOf(Event event, Class<T> type) {
        return event.getDataObjectDeserializer().getObject()
            .filter(type::isInstance)
            .map(type::cast);
    }

    private void handleCheckoutCompleted(Event event) {
        objectOf(event, Session.class).ifPresent(session -> {
            if (session.getCustomer() == null || session.getSubscription() == null) {
                return;
            }

            Server server = findServerByCustomerId(session.getCustomer());
            if (server == null) {
                log.warn("No server found for customer: {}", session.getCustomer());
                return;
            }

            serverMutationHelper.mutate(server, current -> {
                current.setStripeSubscriptionId(session.getSubscription());
                current.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
                current.setPlan(ServerPlan.PREMIUM);
            });
        });
    }

    private void applyPeriodDates(ServerBillingUpdate current, Subscription subscription) {
        Date periodStart = stripeService.extractPeriodStart(subscription);
        Date periodEnd = stripeService.extractPeriodEnd(subscription);
        if (periodStart != null) {
            current.setCurrentPeriodStart(periodStart);
        }
        if (periodEnd != null) {
            current.setCurrentPeriodEnd(periodEnd);
        }
    }

    private Server findServerByCustomerId(String customerId) {
        return serverLookupRepository.findByStripeCustomerId(customerId).orElse(null);
    }

    private Server resolveServer(Subscription subscription) {
        Server server = findServerBySubscriptionId(subscription.getId());
        if (server != null) {
            return server;
        }
        String customerId = subscription.getCustomer();
        if (customerId == null) {
            return null;
        }
        server = findServerByCustomerId(customerId);
        if (server != null && server.getStripeSubscriptionId() == null) {
            serverMutationHelper.mutate(server, current -> current.setStripeSubscriptionId(subscription.getId()));
        }
        return server;
    }

    private void handleSubscriptionCreated(Event event) {
        objectOf(event, Subscription.class).ifPresent(subscription -> {
            if (subscription.getCustomer() == null) {
                return;
            }

            Server server = findServerByCustomerId(subscription.getCustomer());
            if (server == null) {
                return;
            }

            serverMutationHelper.mutate(server, current -> {
                current.setStripeSubscriptionId(subscription.getId());
                current.setSubscriptionStatus(SubscriptionStatus.fromStripeOrInactive(subscription.getStatus()));
                current.setPlan(planForSubscriptionStatus(subscription.getStatus()));
                applyPeriodDates(current, subscription);
            });
        });
    }

    private ServerPlan planForSubscriptionStatus(String status) {
        return isFreeStatus(status) ? ServerPlan.FREE : ServerPlan.PREMIUM;
    }

    private boolean isFreeStatus(String status) {
        return "past_due".equals(status)
               || "unpaid".equals(status)
               || "incomplete".equals(status)
               || "incomplete_expired".equals(status);
    }

    private void handleSubscriptionUpdated(Event event) {
        objectOf(event, Subscription.class).ifPresent(subscription -> {
            Server server = resolveServer(subscription);
            if (server == null) {
                log.warn("No server found for subscription: {}", subscription.getId());
                return;
            }

            String effectiveStatus = stripeService.getEffectiveStatus(subscription);
            serverMutationHelper.mutate(server, current -> {
                current.setSubscriptionStatus(SubscriptionStatus.fromStripeOrInactive(effectiveStatus));
                if (isPremiumStatus(effectiveStatus)) {
                    current.setPlan(ServerPlan.PREMIUM);
                } else if (isFreeStatus(effectiveStatus)) {
                    current.setPlan(ServerPlan.FREE);
                }

                applyPeriodDates(current, subscription);
            });
        });
    }

    private Server findServerBySubscriptionId(String subscriptionId) {
        return serverLookupRepository.findByStripeSubscriptionId(subscriptionId).orElse(null);
    }

    private boolean isPremiumStatus(String status) {
        return "active".equals(status) || "trialing".equals(status) || "paused".equals(status);
    }

    private void handleSubscriptionDeleted(Event event) {
        objectOf(event, Subscription.class).ifPresent(subscription -> {
            Server server = resolveServer(subscription);
            if (server == null) {
                return;
            }

            serverMutationHelper.mutate(server, current -> {
                current.setSubscriptionStatus(SubscriptionStatus.INACTIVE);
                current.setPlan(ServerPlan.FREE);
                current.setCurrentPeriodEnd(null);
            });
            usageTrackingService.resetUsageCounters(server.getId());
        });
    }

    private void handlePaymentFailed(Event event) {
        objectOf(event, Invoice.class).ifPresent(invoice -> {
            if (invoice.getCustomer() == null) {
                return;
            }

            Server server = findServerByCustomerId(invoice.getCustomer());
            if (server == null) {
                return;
            }

            serverMutationHelper.mutate(server, current -> {
                current.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
                current.setPlan(ServerPlan.FREE);
            });
        });
    }

    private void handlePaymentSucceeded(Event event) {
        objectOf(event, Invoice.class).ifPresent(invoice -> {
            if (invoice.getCustomer() == null) {
                return;
            }

            Server server = findServerByCustomerId(invoice.getCustomer());
            if (server == null) {
                return;
            }

            String subscriptionId = extractInvoiceSubscriptionId(invoice);
            if (subscriptionId == null) {
                subscriptionId = server.getStripeSubscriptionId();
            }

            if (subscriptionId == null) {
                unstickPastDue(server);
                return;
            }

            boolean alreadyActive = server.getSubscriptionStatus() == SubscriptionStatus.ACTIVE
                                    && server.getPlan() == ServerPlan.PREMIUM;
            if (alreadyActive) {
                return;
            }

            try {
                Subscription subscription = stripeService.retrieveSubscription(subscriptionId);
                String effectiveStatus = stripeService.getEffectiveStatus(subscription);
                if (isPremiumStatus(effectiveStatus)) {
                    serverMutationHelper.mutate(server, current -> {
                        current.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
                        current.setPlan(ServerPlan.PREMIUM);
                        applyPeriodDates(current, subscription);
                        if (current.getStripeSubscriptionId() == null) {
                            current.setStripeSubscriptionId(subscription.getId());
                        }
                    });
                } else {
                    unstickPastDue(server);
                }
            } catch (StripeException exception) {
                throw new ExternalServiceException("Failed to sync subscription state on Stripe payment success", exception);
            }
        });
    }

    private void unstickPastDue(Server server) {
        if (server.getSubscriptionStatus() == SubscriptionStatus.PAST_DUE) {
            serverMutationHelper.mutate(server, current -> {
                current.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
                current.setPlan(ServerPlan.PREMIUM);
            });
        }
    }

    private String extractInvoiceSubscriptionId(Invoice invoice) {
        Invoice.Parent parent = invoice.getParent();
        if (parent == null || parent.getSubscriptionDetails() == null) {
            return null;
        }
        return parent.getSubscriptionDetails().getSubscription();
    }
}
