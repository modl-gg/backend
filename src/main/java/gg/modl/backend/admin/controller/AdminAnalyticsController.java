package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.dto.request.ExportAnalyticsRequest;
import gg.modl.backend.admin.dto.request.GenerateReportRequest;
import gg.modl.backend.admin.service.AdminAnalyticsService;
import gg.modl.backend.rest.RESTMappingV1;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_ANALYTICS)
@RequiredArgsConstructor
@Slf4j
public class AdminAnalyticsController {
    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@RequestParam(defaultValue = "30d") String range) {
        try {
            return ResponseEntity.ok(adminAnalyticsService.getDashboard(range));
        } catch (Exception e) {
            log.error("Analytics dashboard error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch analytics data"));
        }
    }

    @GetMapping("/usage")
    public ResponseEntity<?> getUsage() {
        try {
            return ResponseEntity.ok(adminAnalyticsService.getUsage());
        } catch (Exception e) {
            log.error("Usage statistics error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch usage statistics"));
        }
    }

    @GetMapping("/historical")
    public ResponseEntity<?> getHistorical(
            @RequestParam(required = false) String metric,
            @RequestParam(defaultValue = "30d") String range) {
        try {
            Map<String, Object> response = adminAnalyticsService.getHistorical(metric, range);
            if (Boolean.FALSE.equals(response.get("success"))) {
                return ResponseEntity.badRequest().body(response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Historical data error", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to fetch historical data"));
        }
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportAnalytics(@RequestBody @Valid ExportAnalyticsRequest request) {
        String type = request.type() != null ? request.type() : "json";
        String range = request.range() != null ? request.range() : "30d";

        if ("csv".equals(type)) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=\"modl-analytics-" + range + ".csv\"")
                    .body("Date,Servers,Users,Tickets\n2024-01-01,100,1500,820");
        } else if ("json".equals(type)) {
            return ResponseEntity.ok(Map.of(
                    "exportDate", new Date().toString(),
                    "range", range,
                    "data", Map.of("servers", 100, "users", 1500, "tickets", 820)
            ));
        }

        return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid export type"));
    }

    @PostMapping("/report")
    public ResponseEntity<?> generateReport(@RequestBody @Valid GenerateReportRequest request) {
        return ResponseEntity.status(501).body(Map.of("success", false, "error", "Report generation not implemented"));
    }
}
