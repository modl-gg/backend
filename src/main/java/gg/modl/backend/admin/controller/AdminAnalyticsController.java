package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.dto.request.ExportAnalyticsRequest;
import gg.modl.backend.admin.dto.request.GenerateReportRequest;
import gg.modl.backend.admin.service.AdminAnalyticsService;
import gg.modl.backend.rest.RESTMappingV1;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_ANALYTICS)
@RequiredArgsConstructor
public class AdminAnalyticsController {
    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(adminAnalyticsService.getDashboard(range));
    }

    @GetMapping("/activity")
    public ResponseEntity<?> getActivity(@RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(adminAnalyticsService.getActivity(range));
    }

    @GetMapping("/usage")
    public ResponseEntity<?> getUsage() {
        return ResponseEntity.ok(adminAnalyticsService.getUsage());
    }

    @GetMapping("/historical")
    public ResponseEntity<?> getHistorical(
        @RequestParam(required = false) String metric,
        @RequestParam(defaultValue = "30d") String range) {
        Map<String, Object> response = adminAnalyticsService.getHistorical(metric, range);
        if (Boolean.FALSE.equals(response.get("success"))) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportAnalytics(@RequestBody @Valid ExportAnalyticsRequest request) {
        String type = request.type() != null ? request.type() : "json";
        String range = request.range() != null ? request.range() : "30d";

        Object result = adminAnalyticsService.exportAnalytics(type, range);

        if ("csv".equals(type)) {
            return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"modl-analytics-" + range + ".csv\"")
                .body(result);
        } else if ("json".equals(type)) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid export type"));
    }

    @PostMapping("/report")
    public ResponseEntity<?> generateReport(@RequestBody @Valid GenerateReportRequest request) {
        return ResponseEntity.status(501).body(Map.of("success", false, "error", "Report generation not implemented"));
    }
}
