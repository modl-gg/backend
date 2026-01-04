package gg.modl.backend.billing.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.billingportal.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.billingportal.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.ConsentCollection;
import gg.modl.backend.billing.config.StripeConfiguration;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {
    private final StripeConfiguration config;

    public boolean isConfigured() {
        return config.isConfigured();
    }

    public String createCustomer(Server server) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(server.getAdminEmail())
                .setName(server.getServerName())
                .putMetadata("serverName", server.getCustomDomain())
                .build();

        Customer customer = Customer.create(params);
        return customer.getId();
    }

    public com.stripe.model.checkout.Session createCheckoutSession(String customerId, String subdomain) throws StripeException {
        String successUrl = String.format("https://%s.%s/panel/settings?session_id={CHECKOUT_SESSION_ID}", subdomain, config.getDomain());
        String cancelUrl = String.format("https://%s.%s/panel/settings", subdomain, config.getDomain());

        com.stripe.param.checkout.SessionCreateParams params = com.stripe.param.checkout.SessionCreateParams.builder()
                .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                .setAllowPromotionCodes(true)
                .setConsentCollection(
                        ConsentCollection.builder()
                                .setTermsOfService(ConsentCollection.TermsOfService.REQUIRED)
                                .build()
                )
                .addLineItem(
                        com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                                .setPrice(config.getPriceId())
                                .setQuantity(1L)
                                .build()
                )
                .setCustomer(customerId)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .build();

        return com.stripe.model.checkout.Session.create(params);
    }

    public Session createPortalSession(String customerId, String subdomain) throws StripeException {
        String returnUrl = String.format("https://%s.%s/panel/settings", subdomain, config.getDomain());

        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(returnUrl)
                .build();

        return Session.create(params);
    }

    public Subscription cancelSubscription(String subscriptionId) throws StripeException {
        Subscription subscription = Subscription.retrieve(subscriptionId);
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build();
        return subscription.update(params);
    }

    public Subscription retrieveSubscription(String subscriptionId) throws StripeException {
        return Subscription.retrieve(subscriptionId);
    }

    public Subscription reactivateSubscription(String subscriptionId) throws StripeException {
        Subscription subscription = Subscription.retrieve(subscriptionId);
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(false)
                .build();
        return subscription.update(params);
    }

    public Subscription createSubscription(String customerId) throws StripeException {
        SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(
                        SubscriptionCreateParams.Item.builder()
                                .setPrice(config.getPriceId())
                                .build()
                )
                .build();

        return Subscription.create(params);
    }

    public Date extractPeriodStart(Subscription subscription) {
        if (subscription.getItems() != null && !subscription.getItems().getData().isEmpty()) {
            SubscriptionItem item = subscription.getItems().getData().get(0);
            if (item.getCurrentPeriodStart() != null) {
                return new Date(item.getCurrentPeriodStart() * 1000);
            }
        }
        return null;
    }

    public Date extractPeriodEnd(Subscription subscription) {
        if (subscription.getItems() != null && !subscription.getItems().getData().isEmpty()) {
            SubscriptionItem item = subscription.getItems().getData().get(0);
            if (item.getCurrentPeriodEnd() != null) {
                return new Date(item.getCurrentPeriodEnd() * 1000);
            }
        }
        return null;
    }

    public String getEffectiveStatus(Subscription subscription) {
        String status = subscription.getStatus();
        if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()) && "active".equals(status)) {
            return "canceled";
        }
        return status;
    }
}
