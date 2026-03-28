package gg.modl.backend.billing.controller;

import gg.modl.backend.billing.dto.request.UpdateOverageLimitsRequest;
import gg.modl.backend.billing.dto.request.UpdateStorageLimitRequest;
import gg.modl.backend.billing.dto.request.UsageBillingSettingsRequest;
import gg.modl.backend.billing.dto.response.BillingStatusResponse;
import gg.modl.backend.billing.dto.response.CancelResponse;
import gg.modl.backend.billing.dto.response.CheckoutSessionResponse;
import gg.modl.backend.billing.dto.response.PortalSessionResponse;
import gg.modl.backend.billing.dto.response.ResubscribeResponse;
import gg.modl.backend.billing.dto.response.UsageBillingSettingsResponse;
import gg.modl.backend.billing.dto.response.UsageResponse;
import gg.modl.backend.billing.service.BillingService;
import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_BILLING)
@RequiredArgsConstructor
public class PanelBillingController {
    private final BillingService billingService;
    private final UsageTrackingService usageTrackingService;

    @PostMapping("/checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(HttpServletRequest request) {
        billingService.requireStripeConfigured();
        Server server = RequestUtil.getRequestServer(request);
        billingService.requireSuperAdmin(server, RequestUtil.getSessionEmail(request));

        return ResponseEntity.ok(billingService.createCheckoutSession(server));
    }

    @PostMapping("/portal-session")
    public ResponseEntity<PortalSessionResponse> createPortalSession(HttpServletRequest request) {
        billingService.requireStripeConfigured();
        Server server = RequestUtil.getRequestServer(request);
        billingService.requireSuperAdmin(server, RequestUtil.getSessionEmail(request));

        return ResponseEntity.ok(billingService.createPortalSession(server));
    }

    @PostMapping("/cancel")
    public ResponseEntity<CancelResponse> cancelSubscription(HttpServletRequest request) {
        billingService.requireStripeConfigured();
        Server server = RequestUtil.getRequestServer(request);
        billingService.requireSuperAdmin(server, RequestUtil.getSessionEmail(request));

        return ResponseEntity.ok(billingService.cancelSubscription(server));
    }

    @PostMapping("/resubscribe")
    public ResponseEntity<ResubscribeResponse> resubscribe(HttpServletRequest request) {
        billingService.requireStripeConfigured();
        Server server = RequestUtil.getRequestServer(request);
        billingService.requireSuperAdmin(server, RequestUtil.getSessionEmail(request));

        return ResponseEntity.ok(billingService.resubscribe(server));
    }

    @GetMapping("/status")
    public ResponseEntity<BillingStatusResponse> getBillingStatus(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(billingService.getBillingStatus(server));
    }

    @GetMapping("/usage")
    public ResponseEntity<UsageResponse> getUsage(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(usageTrackingService.getUsage(server));
    }

    @PostMapping("/usage-settings")
    public ResponseEntity<UsageBillingSettingsResponse> updateUsageBillingSettings(
        @RequestBody @Valid UsageBillingSettingsRequest settingsRequest,
        HttpServletRequest request
    ) {
        billingService.requireStripeConfigured();
        Server server = RequestUtil.getRequestServer(request);
        billingService.requireSuperAdmin(server, RequestUtil.getSessionEmail(request));

        return ResponseEntity.ok(usageTrackingService.updateUsageBillingSettings(server, settingsRequest.enabled()));
    }

    @PostMapping("/storage-limit")
    public ResponseEntity<?> updateStorageLimit(
        @RequestBody @Valid UpdateStorageLimitRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        billingService.requireSuperAdmin(server, RequestUtil.getSessionEmail(request));

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
        billingService.requireSuperAdmin(server, RequestUtil.getSessionEmail(request));

        long maxStorageLimitBytes = (200L + body.maxStorageOverageGB()) * 1024L * 1024 * 1024;
        usageTrackingService.updateOverageLimits(server, maxStorageLimitBytes, body.maxAiOverageRequests());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "maxStorageLimitBytes", maxStorageLimitBytes,
            "maxAiOverageRequests", body.maxAiOverageRequests()
        ));
    }
}
