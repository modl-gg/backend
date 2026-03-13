package gg.modl.backend.billing.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import gg.modl.backend.billing.config.StripeConfiguration;
import gg.modl.backend.billing.service.StripeService;
import gg.modl.backend.billing.service.StripeWebhookService;
import gg.modl.backend.exception.UnauthorizedException;
import gg.modl.backend.rest.RESTMappingV1;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.WEBHOOKS_STRIPE)
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {
    private final StripeConfiguration config;
    private final StripeService stripeService;
    private final StripeWebhookService stripeWebhookService;

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
        } catch (SignatureVerificationException exception) {
            throw new UnauthorizedException("Webhook signature verification failed: " + exception.getMessage());
        }

        stripeWebhookService.processEvent(event);
        return ResponseEntity.ok(Map.of("received", true));
    }
}