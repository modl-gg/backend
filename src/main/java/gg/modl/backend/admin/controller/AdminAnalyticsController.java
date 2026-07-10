package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.service.AdminAnalyticsService;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.proto.modl.v1.AdminAnalyticsReportResponse;
import gg.modl.proto.modl.v1.ExportAnalyticsRequest;
import gg.modl.proto.modl.v1.GenerateReportRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
        return ResponseEntity.ok(AdminAnalyticsProtoMapper.toDashboardResponse(adminAnalyticsService.getDashboard(range)));
    }

    @GetMapping("/activity")
    public ResponseEntity<?> getActivity(@RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(AdminAnalyticsProtoMapper.toActivityResponse(adminAnalyticsService.getActivity(range)));
    }

    @GetMapping("/usage")
    public ResponseEntity<?> getUsage() {
        return ResponseEntity.ok(AdminAnalyticsProtoMapper.toUsageResponse(adminAnalyticsService.getUsage()));
    }

    @GetMapping("/historical")
    public ResponseEntity<?> getHistorical(
        @RequestParam(required = false) String metric,
        @RequestParam(defaultValue = "30d") String range) {
        Map<String, Object> response = adminAnalyticsService.getHistorical(metric, range);
        if (Boolean.FALSE.equals(response.get("success"))) {
            throw new ValidationException(String.valueOf(response.get("error")));
        }
        return ResponseEntity.ok(AdminAnalyticsProtoMapper.toHistoricalResponse(response));
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportAnalytics(@RequestBody ExportAnalyticsRequest request) {
        String type = request.hasType() ? request.getType() : "json";
        String range = request.hasRange() ? request.getRange() : "30d";

        Object result = adminAnalyticsService.exportAnalytics(type, range);

        if ("csv".equals(type)) {
            return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"modl-analytics-" + range + ".csv\"")
                .body(result);
        } else if ("json".equals(type)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonResult = (Map<String, Object>) result;
            return ResponseEntity.ok(AdminAnalyticsProtoMapper.toExportResponse(jsonResult));
        }

        throw new ValidationException("Invalid export type");
    }

    @PostMapping("/report")
    public ResponseEntity<?> generateReport(@RequestBody GenerateReportRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(AdminAnalyticsReportResponse.newBuilder()
            .setSuccess(false)
            .setError("Report generation not implemented")
            .build());
    }
}
