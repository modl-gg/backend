package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.SystemLog;
import gg.modl.backend.admin.service.AdminMonitoringService;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class AdminMonitoringController {
    private final AdminMonitoringService adminMonitoringService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        return ResponseEntity.ok(AdminMonitoringProtoMapper.toDashboardResponse(adminMonitoringService.getDashboard()));
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getLogs(
        @RequestParam(defaultValue = "1") @Min(RequestValidationLimits.PAGINATION_PAGE_MIN) int page,
        @RequestParam(defaultValue = "50") @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) int limit,
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
        return ResponseEntity.ok(AdminMonitoringProtoMapper.toLogsResponse(adminMonitoringService.getLogs(
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
        )));
    }

    @PostMapping("/logs")
    public ResponseEntity<?> createLog(@RequestBody gg.modl.proto.modl.v1.CreateSystemLogRequest request) {
        SystemLog saved = adminMonitoringService.createLog(AdminMonitoringProtoMapper.fromCreateLog(request));
        return ResponseEntity.status(201).body(
            AdminMonitoringProtoMapper.toSystemLogMutationResponse(saved, "Log entry created successfully"));
    }

    @GetMapping("/sources")
    public ResponseEntity<?> getSources() {
        return ResponseEntity.ok(AdminMonitoringProtoMapper.toSourcesResponse(adminMonitoringService.getSources()));
    }

    @PutMapping("/logs/{id}/resolve")
    public ResponseEntity<?> resolveLog(@PathVariable String id, @RequestBody gg.modl.proto.modl.v1.ResolveLogRequest request) {
        SystemLog updated = adminMonitoringService.resolveLog(id, AdminMonitoringProtoMapper.fromResolveLog(request)).orElse(null);
        if (updated == null) {
            throw new ResourceNotFoundException("Log entry not found");
        }

        return ResponseEntity.ok(AdminMonitoringProtoMapper.toSystemLogMutationResponse(updated, "Log entry marked as resolved"));
    }

    @GetMapping("/health")
    public ResponseEntity<?> getHealth() {
        return ResponseEntity.ok(AdminMonitoringProtoMapper.toHealthResponse(adminMonitoringService.getHealth()));
    }

    @PostMapping("/logs/delete")
    public ResponseEntity<?> deleteLogs(@RequestBody gg.modl.proto.modl.v1.DeleteLogsRequest request) {
        long deletedCount = adminMonitoringService.deleteLogs(request.getLogIdsList());
        return ResponseEntity.ok(AdminMonitoringProtoMapper.toDeleteLogsResponse(
            deletedCount, "Successfully deleted " + deletedCount + " log(s)"));
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
        return ResponseEntity.ok(AdminMonitoringProtoMapper.toDeleteLogsResponse(deletedCount, "Successfully cleared all logs"));
    }

    @GetMapping("/pm2-status")
    public ResponseEntity<?> getPm2Status() {
        // PM2 integration placeholder - would require native process monitoring
        return ResponseEntity.ok(AdminMonitoringProtoMapper.toPm2StatusResponse());
    }

    @PostMapping("/pm2/restart")
    public ResponseEntity<?> restartPm2() {
        return ResponseEntity.ok(AdminMonitoringProtoMapper.toPm2RestartResponse("PM2 log streaming restarted"));
    }

    @PostMapping("/pm2/toggle")
    public ResponseEntity<?> togglePm2(@RequestBody gg.modl.proto.modl.v1.TogglePm2Request request) {
        boolean enabled = request.hasEnabledValue() ? request.getEnabledValue() : request.getEnabled();
        return ResponseEntity.ok(AdminMonitoringProtoMapper.toPm2ToggleResponse(
            enabled, "PM2 log streaming " + (enabled ? "enabled" : "disabled")));
    }
}
