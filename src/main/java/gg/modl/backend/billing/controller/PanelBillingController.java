package gg.modl.backend.billing.controller;

import com.stripe.exception.StripeException;
import gg.modl.backend.billing.dto.request.UpdateOverageLimitsRequest;
import gg.modl.backend.billing.dto.request.UpdateStorageLimitRequest;
import gg.modl.backend.billing.dto.request.UsageBillingSettingsRequest;
import gg.modl.backend.billing.dto.response.*;
import gg.modl.backend.billing.service.BillingService;
import gg.modl.backend.billing.service.StripeService;
import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.service.StorageQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    private final PermissionService permissionService;

    @PostMapping("/checkout-session")
    public ResponseEntity<?> createCheckoutSession(HttpServletRequest request) {
        ResponseEntity<?> stripeCheck = requireStripeConfigured();
        if (stripeCheck != null) return stripeCheck;

        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;

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
        ResponseEntity<?> stripeCheck = requireStripeConfigured();
        if (stripeCheck != null) return stripeCheck;

        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;

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
        ResponseEntity<?> stripeCheck = requireStripeConfigured();
        if (stripeCheck != null) return stripeCheck;

        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;

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
        ResponseEntity<?> stripeCheck = requireStripeConfigured();
        if (stripeCheck != null) return stripeCheck;

        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;

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
        ResponseEntity<?> stripeCheck = requireStripeConfigured();
        if (stripeCheck != null) return stripeCheck;

        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;

        try {
            UsageBillingSettingsResponse response = usageTrackingService.updateUsageBillingSettings(server, settingsRequest.enabled());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/storage-limit")
    public ResponseEntity<?> updateStorageLimit(
            @RequestBody @Valid UpdateStorageLimitRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;

        if (server.getPlan() != ServerPlan.PREMIUM) {
            return ResponseEntity.badRequest().body(Map.of("error", "Storage limit configuration is only available for premium servers"));
        }

        if (body.maxStorageLimitBytes() > StorageQuotaService.MAX_PREMIUM_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "Storage limit cannot exceed 2200 GB. Please contact support for higher limits."));
        }

        usageTrackingService.updateStorageLimit(server, body.maxStorageLimitBytes());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "maxStorageLimitBytes", body.maxStorageLimitBytes()
        ));
    }

    @PostMapping("/overage-limits")
    public ResponseEntity<?> updateOverageLimits(
            @RequestBody @Valid UpdateOverageLimitsRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;

        if (server.getPlan() != ServerPlan.PREMIUM) {
            return ResponseEntity.badRequest().body(Map.of("error", "Overage limits configuration is only available for premium servers"));
        }

        long maxStorageLimitBytes = (200L + body.maxStorageOverageGB()) * 1024L * 1024 * 1024;
        usageTrackingService.updateOverageLimits(server, maxStorageLimitBytes, body.maxAiOverageRequests());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "maxStorageLimitBytes", maxStorageLimitBytes,
                "maxAiOverageRequests", body.maxAiOverageRequests()
        ));
    }

    private ResponseEntity<?> requireStripeConfigured() {
        if (!stripeService.isConfigured()) {
            return ResponseEntity.status(503).body("Billing service unavailable. Stripe not configured.");
        }
        return null;
    }

    private ResponseEntity<?> requireSuperAdmin(Server server, HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (email == null || !permissionService.isSuperAdmin(server, email)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the super admin can manage billing"));
        }
        return null;
    }
}
