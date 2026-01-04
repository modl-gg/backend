package gg.modl.backend.billing.controller;

import com.stripe.exception.StripeException;
import gg.modl.backend.billing.dto.request.UsageBillingSettingsRequest;
import gg.modl.backend.billing.dto.response.*;
import gg.modl.backend.billing.service.BillingService;
import gg.modl.backend.billing.service.StripeService;
import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_BILLING)
@RequiredArgsConstructor
@Slf4j
public class PanelBillingController {
    private final BillingService billingService;
    private final StripeService stripeService;
    private final UsageTrackingService usageTrackingService;

    @PostMapping("/checkout-session")
    public ResponseEntity<?> createCheckoutSession(HttpServletRequest request) {
        if (!stripeService.isConfigured()) {
            return ResponseEntity.status(503).body("Billing service unavailable. Stripe not configured.");
        }

        Server server = RequestUtil.getRequestServer(request);

        try {
            CheckoutSessionResponse response = billingService.createCheckoutSession(server);
            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            log.error("Error creating checkout session", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create checkout session"));
        }
    }

    @PostMapping("/portal-session")
    public ResponseEntity<?> createPortalSession(HttpServletRequest request) {
        if (!stripeService.isConfigured()) {
            return ResponseEntity.status(503).body("Billing service unavailable. Stripe not configured.");
        }

        Server server = RequestUtil.getRequestServer(request);

        try {
            PortalSessionResponse response = billingService.createPortalSession(server);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (StripeException e) {
            log.error("Error creating portal session", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create portal session"));
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancelSubscription(HttpServletRequest request) {
        if (!stripeService.isConfigured()) {
            return ResponseEntity.status(503).body("Billing service unavailable. Stripe not configured.");
        }

        Server server = RequestUtil.getRequestServer(request);

        try {
            CancelResponse response = billingService.cancelSubscription(server);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (StripeException e) {
            log.error("Error cancelling subscription", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to cancel subscription. Please try again or contact support."));
        }
    }

    @PostMapping("/resubscribe")
    public ResponseEntity<?> resubscribe(HttpServletRequest request) {
        if (!stripeService.isConfigured()) {
            return ResponseEntity.status(503).body("Billing service unavailable. Stripe not configured.");
        }

        Server server = RequestUtil.getRequestServer(request);

        try {
            ResubscribeResponse response = billingService.resubscribe(server);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (StripeException e) {
            log.error("Error resubscribing", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to reactivate subscription. Please try again or contact support.",
                    "details", e.getMessage()
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<BillingStatusResponse> getBillingStatus(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        BillingStatusResponse response = billingService.getBillingStatus(server);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/usage")
    public ResponseEntity<?> getUsage(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            UsageResponse response = usageTrackingService.getUsage(server);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/usage-settings")
    public ResponseEntity<?> updateUsageBillingSettings(
            @RequestBody UsageBillingSettingsRequest settingsRequest,
            HttpServletRequest request
    ) {
        if (!stripeService.isConfigured()) {
            return ResponseEntity.status(503).body("Billing service unavailable. Stripe not configured.");
        }

        Server server = RequestUtil.getRequestServer(request);

        try {
            UsageBillingSettingsResponse response = usageTrackingService.updateUsageBillingSettings(server, settingsRequest.enabled());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
