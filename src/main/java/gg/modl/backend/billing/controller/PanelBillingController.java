package gg.modl.backend.billing.controller;

import gg.modl.backend.billing.service.BillingService;
import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.infrastructure.authorization.PanelAccessRule;
import gg.modl.backend.infrastructure.authorization.RequiresPanelPermission;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.BillingStatusResponse;
import gg.modl.proto.modl.v1.CancelResponse;
import gg.modl.proto.modl.v1.CheckoutSessionResponse;
import gg.modl.proto.modl.v1.PortalSessionResponse;
import gg.modl.proto.modl.v1.ResubscribeResponse;
import gg.modl.proto.modl.v1.UpdateOverageLimitsRequest;
import gg.modl.proto.modl.v1.UpdateOverageLimitsResponse;
import gg.modl.proto.modl.v1.UpdateStorageLimitRequest;
import gg.modl.proto.modl.v1.UpdateStorageLimitResponse;
import gg.modl.proto.modl.v1.UsageBillingSettingsRequest;
import gg.modl.proto.modl.v1.UsageBillingSettingsResponse;
import gg.modl.proto.modl.v1.UsageResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_BILLING)
@RequiresPanelPermission(rule = PanelAccessRule.SUPER_ADMIN,
    supersedesPermissions = {PermissionService.ADMIN_SETTINGS_VIEW_BILLING, PermissionService.ADMIN_SETTINGS_MODIFY_BILLING})
@RequiredArgsConstructor
public class PanelBillingController {
    private final BillingService billingService;
    private final UsageTrackingService usageTrackingService;

    @PostMapping("/checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(HttpServletRequest request) {
        billingService.requireStripeConfigured();
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(PanelBillingProtoMapper.toCheckoutSessionResponse(billingService.createCheckoutSession(server)));
    }

    @PostMapping("/portal-session")
    public ResponseEntity<PortalSessionResponse> createPortalSession(HttpServletRequest request) {
        billingService.requireStripeConfigured();
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(PanelBillingProtoMapper.toPortalSessionResponse(billingService.createPortalSession(server)));
    }

    @PostMapping("/cancel")
    public ResponseEntity<CancelResponse> cancelSubscription(HttpServletRequest request) {
        billingService.requireStripeConfigured();
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(PanelBillingProtoMapper.toCancelResponse(billingService.cancelSubscription(server)));
    }

    @PostMapping("/resubscribe")
    public ResponseEntity<ResubscribeResponse> resubscribe(HttpServletRequest request) {
        billingService.requireStripeConfigured();
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(PanelBillingProtoMapper.toResubscribeResponse(billingService.resubscribe(server)));
    }

    @GetMapping("/status")
    public ResponseEntity<BillingStatusResponse> getBillingStatus(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        billingService.reconcileBillingStatus(server);
        return ResponseEntity.ok(PanelBillingProtoMapper.toBillingStatusResponse(billingService.getBillingStatus(server)));
    }

    @GetMapping("/usage")
    public ResponseEntity<UsageResponse> getUsage(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(PanelBillingProtoMapper.toUsageResponse(usageTrackingService.getUsage(server)));
    }

    @PostMapping("/usage-settings")
    public ResponseEntity<UsageBillingSettingsResponse> updateUsageBillingSettings(
        @RequestBody UsageBillingSettingsRequest settingsRequest,
        HttpServletRequest request
    ) {
        billingService.requireStripeConfigured();
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(PanelBillingProtoMapper.toUsageBillingSettingsResponse(
            usageTrackingService.updateUsageBillingSettings(server, settingsRequest.getEnabled())));
    }

    @PostMapping("/storage-limit")
    public ResponseEntity<UpdateStorageLimitResponse> updateStorageLimit(
        @RequestBody UpdateStorageLimitRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        long maxStorageLimitBytes = body.getMaxStorageLimitBytes();
        usageTrackingService.updateStorageLimit(server, maxStorageLimitBytes);

        return ResponseEntity.ok(PanelBillingProtoMapper.toUpdateStorageLimitResponse(maxStorageLimitBytes));
    }

    @PostMapping("/overage-limits")
    public ResponseEntity<UpdateOverageLimitsResponse> updateOverageLimits(
        @RequestBody UpdateOverageLimitsRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        int maxStorageOverageGB = body.hasMaxStorageOverageGbValue() ? body.getMaxStorageOverageGbValue() : body.getMaxStorageOverageGb();
        int maxAiOverageRequests = body.hasMaxAiOverageRequestsValue() ? body.getMaxAiOverageRequestsValue() : body.getMaxAiOverageRequests();

        long maxStorageLimitBytes = usageTrackingService.updateOverageLimits(server, maxStorageOverageGB, maxAiOverageRequests);

        return ResponseEntity.ok(PanelBillingProtoMapper.toUpdateOverageLimitsResponse(maxStorageLimitBytes, maxAiOverageRequests));
    }
}
