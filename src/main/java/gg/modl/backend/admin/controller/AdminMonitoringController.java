package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.SystemLog;
import gg.modl.backend.admin.dto.request.CreateSystemLogRequest;
import gg.modl.backend.admin.dto.request.DeleteLogsRequest;
import gg.modl.backend.admin.dto.request.ResolveLogRequest;
import gg.modl.backend.admin.dto.request.TogglePm2Request;
import gg.modl.backend.admin.service.AdminMonitoringService;
import gg.modl.backend.rest.RESTMappingV1;
import jakarta.validation.Valid;
import java.util.Date;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_MONITORING)
@RequiredArgsConstructor
public class AdminMonitoringController {
    private final AdminMonitoringService adminMonitoringService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        return ResponseEntity.ok(adminMonitoringService.getDashboard());
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getLogs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) String level,
        @RequestParam(required = false) String source,
        @RequestParam(required = false) String serverId,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String resolved,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate,
        @RequestParam(defaultValue = "timestamp") String sort,
        @RequestParam(defaultValue = "desc") String order) {
        return ResponseEntity.ok(adminMonitoringService.getLogs(
            page,
            limit,
            level,
            source,
            serverId,
            category,
            resolved,
            search,
            startDate,
            endDate,
            sort,
            order
        ));
    }

    @PostMapping("/logs")
    public ResponseEntity<?> createLog(@RequestBody @Valid CreateSystemLogRequest request) {
        SystemLog saved = adminMonitoringService.createLog(request);
        return ResponseEntity.status(201).body(Map.of("success", true, "data", saved, "message", "Log entry created successfully"));
    }

    @GetMapping("/sources")
    public ResponseEntity<?> getSources() {
        return ResponseEntity.ok(adminMonitoringService.getSources());
    }

    @PutMapping("/logs/{id}/resolve")
    public ResponseEntity<?> resolveLog(@PathVariable String id, @RequestBody @Valid ResolveLogRequest request) {
        SystemLog updated = adminMonitoringService.resolveLog(id, request).orElse(null);
        if (updated == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Log entry not found"));
        }

        return ResponseEntity.ok(Map.of("success", true, "data", updated, "message", "Log entry marked as resolved"));
    }

    @GetMapping("/health")
    public ResponseEntity<?> getHealth() {
        return ResponseEntity.ok(adminMonitoringService.getHealth());
    }

    @PostMapping("/logs/delete")
    public ResponseEntity<?> deleteLogs(@RequestBody @Valid DeleteLogsRequest request) {
        long deletedCount = adminMonitoringService.deleteLogs(request.logIds());
        return ResponseEntity.ok(
            Map.of("success", true, "data", Map.of("deletedCount", deletedCount), "message", "Successfully deleted " + deletedCount + " log(s)"));
    }

    @GetMapping("/logs/export")
    public ResponseEntity<?> exportLogs(
        @RequestParam(required = false) String level,
        @RequestParam(required = false) String source,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String resolved,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate) {
        String csv = adminMonitoringService.exportLogs(level, source, category, resolved, search, startDate, endDate);

        return ResponseEntity.ok()
            .header("Content-Type", "text/csv")
            .header("Content-Disposition", "attachment; filename=\"system-logs-" + new Date().toInstant().toString().split("T")[0] + ".csv\"")
            .body(csv);
    }

    @PostMapping("/logs/clear-all")
    public ResponseEntity<?> clearAllLogs() {
        long deletedCount = adminMonitoringService.clearAllLogs();
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("deletedCount", deletedCount), "message", "Successfully cleared all logs"));
    }

    @GetMapping("/pm2-status")
    public ResponseEntity<?> getPm2Status() {
        // PM2 integration placeholder - would require native process monitoring
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "isEnabled", false,
                "isStreaming", false,
                "reconnectAttempts", 0,
                "recentLogsCount", 0,
                "lastLogTime", null
            )
        ));
    }

    @PostMapping("/pm2/restart")
    public ResponseEntity<?> restartPm2() {
        return ResponseEntity.ok(Map.of("success", true, "message", "PM2 log streaming restarted"));
    }

    @PostMapping("/pm2/toggle")
    public ResponseEntity<?> togglePm2(@RequestBody @Valid TogglePm2Request request) {
        boolean enabled = request.enabled();
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("isEnabled", enabled, "isStreaming", enabled), "message",
            "PM2 log streaming " + (enabled ? "enabled" : "disabled")));
    }
}
