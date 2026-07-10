package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.admin.dto.request.ToggleMaintenanceRequest;
import gg.modl.backend.admin.service.GlobalSystemService;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_SYSTEM)
@RequiredArgsConstructor
@Slf4j
public class AdminSystemController {
    private final GlobalSystemService globalSystemService;

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        SystemConfig config = globalSystemService.getOrCreateConfig();
        return ResponseEntity.ok(AdminSystemProtoMapper.toConfigResponse(config, null));
    }

    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody gg.modl.proto.modl.v1.UpdateSystemConfigRequest request) {
        SystemConfig saved = globalSystemService.updateConfig(AdminSystemProtoMapper.fromUpdateConfig(request));
        log.info("Configuration updated by admin");
        return ResponseEntity.ok(AdminSystemProtoMapper.toConfigResponse(saved, "Configuration updated successfully"));
    }

    @GetMapping("/maintenance")
    public ResponseEntity<?> getMaintenanceStatus() {
        return ResponseEntity.ok(AdminSystemProtoMapper.toMaintenanceResponse(globalSystemService.getMaintenanceStatus(), null));
    }

    @PostMapping("/maintenance/toggle")
    public ResponseEntity<?> toggleMaintenance(@RequestBody gg.modl.proto.modl.v1.ToggleMaintenanceRequest request) {
        ToggleMaintenanceRequest domainRequest = AdminSystemProtoMapper.fromToggleMaintenance(request);
        boolean enabled = domainRequest.enabled();
        Map<String, Object> data = globalSystemService.toggleMaintenance(domainRequest);
        log.info("Maintenance mode {} by admin", enabled ? "enabled" : "disabled");
        return ResponseEntity.ok(AdminSystemProtoMapper.toMaintenanceResponse(
            data, "Maintenance mode " + (enabled ? "enabled" : "disabled")));
    }

    @GetMapping("/rate-limits")
    public ResponseEntity<?> getRateLimits() {
        return ResponseEntity.ok(AdminSystemProtoMapper.toRateLimitsResponse(globalSystemService.getRateLimitStatus()));
    }

    @PutMapping("/rate-limits")
    public ResponseEntity<?> updateRateLimits(@RequestBody gg.modl.proto.modl.v1.UpdateRateLimitsRequest request) {
        SystemConfig.PerformanceConfig performanceConfig = globalSystemService.updateRateLimits(
            AdminSystemProtoMapper.fromUpdateRateLimits(request));
        log.info("Rate limits updated by admin");
        return ResponseEntity.ok(AdminSystemProtoMapper.toRateLimitsUpdateResponse(performanceConfig, "Rate limits updated successfully"));
    }

    @GetMapping("/prompts")
    public ResponseEntity<?> getPrompt() {
        SystemPrompt prompt = globalSystemService.getPrompt();
        return ResponseEntity.ok(AdminSystemProtoMapper.toPromptResponse(prompt, null));
    }

    @PutMapping("/prompts")
    public ResponseEntity<?> updatePrompt(@RequestBody gg.modl.proto.modl.v1.UpdatePromptRequest request) {
        SystemPrompt updated = globalSystemService.updatePrompt(AdminSystemProtoMapper.fromUpdatePrompt(request));
        log.info("System prompt updated");
        return ResponseEntity.ok(AdminSystemProtoMapper.toPromptResponse(updated, "System prompt updated successfully"));
    }

    @PostMapping("/prompts/reset")
    public ResponseEntity<?> resetPrompt() {
        SystemPrompt reset = globalSystemService.resetPrompt();
        log.info("System prompt reset to default");
        return ResponseEntity.ok(AdminSystemProtoMapper.toPromptResponse(reset, "System prompt reset to default"));
    }

    @PostMapping("/services/{service}/restart")
    public ResponseEntity<?> restartService(@PathVariable String service) {
        List<String> allowedServices = List.of("api", "worker", "scheduler", "cache", "database");
        if (!allowedServices.contains(service)) {
            throw new ValidationException("Invalid service name. Allowed: " + String.join(", ", allowedServices));
        }

        log.info("Service restart requested for: {} by admin", service);

        return ResponseEntity.ok(AdminSystemProtoMapper.toServiceRestartResponse(
            service, "restarting", new Date(), "Service " + service + " restart initiated"));
    }
}
