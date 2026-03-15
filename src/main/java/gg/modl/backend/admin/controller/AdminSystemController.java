package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.admin.dto.request.ToggleMaintenanceRequest;
import gg.modl.backend.admin.dto.request.UpdatePromptRequest;
import gg.modl.backend.admin.dto.request.UpdateRateLimitsRequest;
import gg.modl.backend.admin.dto.request.UpdateSystemConfigRequest;
import gg.modl.backend.admin.service.GlobalSystemService;
import gg.modl.backend.rest.RESTMappingV1;
import jakarta.validation.Valid;
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
        return ResponseEntity.ok(Map.of("success", true, "data", config));
    }

    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody @Valid UpdateSystemConfigRequest request) {
        SystemConfig saved = globalSystemService.updateConfig(request);
        log.info("Configuration updated by admin");
        return ResponseEntity.ok(Map.of("success", true, "data", saved, "message", "Configuration updated successfully"));
    }

    @GetMapping("/maintenance")
    public ResponseEntity<?> getMaintenanceStatus() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", globalSystemService.getMaintenanceStatus()
        ));
    }

    @PostMapping("/maintenance/toggle")
    public ResponseEntity<?> toggleMaintenance(@RequestBody @Valid ToggleMaintenanceRequest request) {
        boolean enabled = request.enabled();
        Map<String, Object> data = globalSystemService.toggleMaintenance(request);
        log.info("Maintenance mode {} by admin", enabled ? "enabled" : "disabled");
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", data,
            "message", "Maintenance mode " + (enabled ? "enabled" : "disabled")
        ));
    }

    @GetMapping("/rate-limits")
    public ResponseEntity<?> getRateLimits() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", globalSystemService.getRateLimitStatus()
        ));
    }

    @PutMapping("/rate-limits")
    public ResponseEntity<?> updateRateLimits(@RequestBody @Valid UpdateRateLimitsRequest request) {
        SystemConfig.PerformanceConfig performanceConfig = globalSystemService.updateRateLimits(request);
        log.info("Rate limits updated by admin");
        return ResponseEntity.ok(Map.of("success", true, "data", performanceConfig, "message", "Rate limits updated successfully"));
    }

    @GetMapping("/prompts")
    public ResponseEntity<?> getPrompt() {
        SystemPrompt prompt = globalSystemService.getPrompt();
        return ResponseEntity.ok(Map.of("success", true, "data", prompt != null ? prompt : Map.of()));
    }

    @PutMapping("/prompts")
    public ResponseEntity<?> updatePrompt(@RequestBody @Valid UpdatePromptRequest request) {
        SystemPrompt updated = globalSystemService.updatePrompt(request);
        log.info("System prompt updated");
        return ResponseEntity.ok(Map.of("success", true, "data", updated, "message", "System prompt updated successfully"));
    }

    @PostMapping("/prompts/reset")
    public ResponseEntity<?> resetPrompt() {
        SystemPrompt reset = globalSystemService.resetPrompt();
        log.info("System prompt reset to default");
        return ResponseEntity.ok(Map.of("success", true, "data", reset, "message", "System prompt reset to default"));
    }

    @PostMapping("/services/{service}/restart")
    public ResponseEntity<?> restartService(@PathVariable String service) {
        List<String> allowedServices = List.of("api", "worker", "scheduler", "cache", "database");
        if (!allowedServices.contains(service)) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Invalid service name. Allowed: " + String.join(", ", allowedServices)
            ));
        }

        log.info("Service restart requested for: {} by admin", service);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "service", service,
                "status", "restarting",
                "requestedAt", new Date()
            ),
            "message", "Service " + service + " restart initiated"
        ));
    }
}
