package gg.modl.backend.billing.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.billing.dto.response.BillingStatusResponse;
import gg.modl.backend.billing.dto.response.CancelResponse;
import gg.modl.backend.billing.dto.response.CheckoutSessionResponse;
import gg.modl.backend.billing.dto.response.PortalSessionResponse;
import gg.modl.backend.billing.dto.response.ResubscribeResponse;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import gg.modl.backend.server.service.ServerMutationHelper;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {
    private final StripeService stripeService;
    private final ServerMutationHelper serverMutationHelper;
    private final PermissionService permissionService;

    public void requireStripeConfigured() {
        if (!stripeService.isConfigured()) {
            throw new ExternalServiceException("Billing service unavailable. Stripe not configured.");
        }
    }

    public void requireSuperAdmin(Server server, String email) {
        if (email == null || !permissionService.isSuperAdmin(server, email)) {
            throw new ForbiddenException("Only the super admin can manage billing");
        }
    }

    public void syncCustomerEmail(Server server, String newEmail) {
        String customerId = server.getStripeCustomerId();
        if (!stripeService.isConfigured() || customerId == null || customerId.isBlank()) {
            return;
        }

        try {
            stripeService.updateCustomerEmail(customerId, newEmail);
        } catch (StripeException e) {
            log.warn("Failed to sync Stripe customer email for server {}; billing email may be stale", server.getId(), e);
        }
    }

    public CheckoutSessionResponse createCheckoutSession(Server server) {
        try {
            String customerId = server.getStripeCustomerId();

            if (customerId == null || customerId.isBlank()) {
                customerId = stripeService.createCustomer(server);
                String createdCustomerId = customerId;
                serverMutationHelper.mutate(server, current -> current.setStripeCustomerId(createdCustomerId));
            }

            Session session = stripeService.createCheckoutSession(customerId, server.getCustomDomain());
            return new CheckoutSessionResponse(session.getId(), session.getUrl());
        } catch (StripeException e) {
            throw new ExternalServiceException("Failed to create checkout session", e);
        }
    }

    public PortalSessionResponse createPortalSession(Server server) {
        if (server.getStripeCustomerId() == null || server.getStripeCustomerId().isBlank()) {
            throw new ResourceNotFoundException("Customer ID not found for server");
        }

        try {
            com.stripe.model.billingportal.Session session = stripeService.createPortalSession(server.getStripeCustomerId(), server.getCustomDomain());
            return new PortalSessionResponse(session.getUrl());
        } catch (StripeException e) {
            throw new ExternalServiceException("Failed to create portal session", e);
        }
    }

    public CancelResponse cancelSubscription(Server server) {
        if (server.getStripeSubscriptionId() == null || server.getStripeSubscriptionId().isBlank()) {
            throw new ResourceNotFoundException("No active subscription found to cancel");
        }

        try {
            Subscription canceledSubscription = stripeService.cancelSubscription(server.getStripeSubscriptionId());

            Date periodEndDate = server.getCurrentPeriodEnd();
            if (periodEndDate == null) {
                periodEndDate = stripeService.extractPeriodEnd(canceledSubscription);
            }

            Date finalPeriodEndDate = periodEndDate;
            serverMutationHelper.mutate(server, current -> {
                current.setSubscriptionStatus(SubscriptionStatus.CANCELED);
                if (finalPeriodEndDate != null) {
                    current.setCurrentPeriodEnd(finalPeriodEndDate);
                }
            });

            return new CancelResponse(
                true,
                "Subscription cancelled successfully. Access will continue until the end of your current billing period.",
                periodEndDate
            );
        } catch (StripeException e) {
            throw new ExternalServiceException("Failed to cancel subscription", e);
        }
    }

    public BillingStatusResponse getBillingStatus(Server server) {
        SubscriptionStatus currentStatus = server.getSubscriptionStatus();
        return new BillingStatusResponse(
            server.getPlan() != null ? server.getPlan().name() : null,
            currentStatus != null ? currentStatus.name() : null,
            server.getCurrentPeriodStart(),
            server.getCurrentPeriodEnd(),
            server.getCustomDomainGrandfathered(),
            server.getMaxStorageLimitBytes(),
            server.getMaxAiOverageRequests()
        );
    }

    @Async
    public void reconcileBillingStatus(Server server) {
        SubscriptionStatus currentStatus = server.getSubscriptionStatus();
        boolean reconcilable = server.getStripeSubscriptionId() != null
            && (currentStatus == null || currentStatus == SubscriptionStatus.ACTIVE || currentStatus == SubscriptionStatus.CANCELED)
            && stripeService.isConfigured();
        if (!reconcilable) {
            return;
        }

        try {
            Subscription subscription = stripeService.retrieveSubscription(server.getStripeSubscriptionId());
            SubscriptionStatus effectiveStatus = SubscriptionStatus.fromStripeOrInactive(stripeService.getEffectiveStatus(subscription));
            Date periodStartDate = stripeService.extractPeriodStart(subscription);
            Date periodEndDate = stripeService.extractPeriodEnd(subscription);

            boolean needsUpdate = effectiveStatus != currentStatus
                                  || periodDrifted(server.getCurrentPeriodStart(), periodStartDate)
                                  || periodDrifted(server.getCurrentPeriodEnd(), periodEndDate);

            if (needsUpdate) {
                serverMutationHelper.mutate(server, current -> {
                    current.setSubscriptionStatus(effectiveStatus);
                    if (periodStartDate != null) {
                        current.setCurrentPeriodStart(periodStartDate);
                    }
                    if (periodEndDate != null) {
                        current.setCurrentPeriodEnd(periodEndDate);
                    }
                });
            }
        } catch (StripeException exception) {
            log.error("Error reconciling subscription from Stripe for server {}", server.getId(), exception);
        }
    }

    private boolean periodDrifted(Date persisted, Date fresh) {
        return fresh != null && (persisted == null || Math.abs(persisted.getTime() - fresh.getTime()) > 1000);
    }

    public ResubscribeResponse resubscribe(Server server) {
        if (server.getSubscriptionStatus() != SubscriptionStatus.CANCELED) {
            throw new ConflictException("No cancelled subscription found to reactivate.");
        }

        try {
            Subscription subscriptionResult;

            if (server.getStripeSubscriptionId() != null) {
                try {
                    Subscription existingSubscription = stripeService.retrieveSubscription(server.getStripeSubscriptionId());

                    if ("active".equals(existingSubscription.getStatus()) && Boolean.TRUE.equals(existingSubscription.getCancelAtPeriodEnd())) {
                        subscriptionResult = stripeService.reactivateSubscription(server.getStripeSubscriptionId());
                    } else if ("canceled".equals(existingSubscription.getStatus())) {
                        subscriptionResult = createNewSubscription(server);
                    } else {
                        throw new ConflictException("Subscription is not in a cancelled state that can be reactivated.");
                    }
                } catch (StripeException exception) {
                    if ("resource_missing".equals(exception.getCode())) {
                        subscriptionResult = createNewSubscription(server);
                    } else {
                        throw exception;
                    }
                }
            } else {
                subscriptionResult = createNewSubscription(server);
            }

            Date periodStartDate = stripeService.extractPeriodStart(subscriptionResult);
            Date periodEndDate = stripeService.extractPeriodEnd(subscriptionResult);
            String subscriptionId = subscriptionResult.getId();
            SubscriptionStatus subscriptionStatus = SubscriptionStatus.fromStripeOrInactive(subscriptionResult.getStatus());

            serverMutationHelper.mutate(server, current -> {
                current.setStripeSubscriptionId(subscriptionId);
                current.setSubscriptionStatus(subscriptionStatus);
                current.setPlan(ServerPlan.PREMIUM);
                if (periodStartDate != null) {
                    current.setCurrentPeriodStart(periodStartDate);
                }
                if (periodEndDate != null) {
                    current.setCurrentPeriodEnd(periodEndDate);
                }
            });

            return new ResubscribeResponse(
                true,
                "Subscription reactivated successfully! Your premium features are now active.",
                new ResubscribeResponse.SubscriptionInfo(
                    subscriptionResult.getId(),
                    subscriptionResult.getStatus(),
                    periodEndDate
                )
            );
        } catch (StripeException e) {
            throw new ExternalServiceException("Failed to resubscribe", e);
        }
    }

    private Subscription createNewSubscription(Server server) throws StripeException {
        if (server.getStripeCustomerId() == null || server.getStripeCustomerId().isBlank()) {
            throw new ResourceNotFoundException("No Stripe customer ID found. Cannot create subscription.");
        }
        return stripeService.createSubscription(server.getStripeCustomerId());
    }
}
