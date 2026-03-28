package gg.modl.backend.analytics.controller;

import gg.modl.backend.analytics.dto.response.AuditLogsAnalyticsResponse;
import gg.modl.backend.analytics.dto.response.OverviewResponse;
import gg.modl.backend.analytics.dto.response.PlayerActivityResponse;
import gg.modl.backend.analytics.dto.response.PunishmentAnalyticsResponse;
import gg.modl.backend.analytics.dto.response.TicketAnalyticsResponse;
import gg.modl.backend.analytics.service.AnalyticsService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_ANALYTICS)
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ResponseEntity<Map<String, OverviewResponse>> getOverview(HttpServletRequest request) {
        final Server server = RequestUtil.getRequestServer(request);
        final OverviewResponse overview = analyticsService.getOverview(server);

        return ResponseEntity.ok(Map.of("overview", overview));
    }

    @GetMapping("/tickets")
    public ResponseEntity<TicketAnalyticsResponse> getTicketAnalytics(
        @RequestParam(defaultValue = "30d") String period,
        HttpServletRequest request
    ) {
        final Server server = RequestUtil.getRequestServer(request);
        final TicketAnalyticsResponse analytics = analyticsService.getTicketAnalytics(server, period);

        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/punishments")
    public ResponseEntity<PunishmentAnalyticsResponse> getPunishmentAnalytics(
        @RequestParam(defaultValue = "30d") String period,
        HttpServletRequest request
    ) {
        final Server server = RequestUtil.getRequestServer(request);
        final PunishmentAnalyticsResponse analytics = analyticsService.getPunishmentAnalytics(server, period);

        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<AuditLogsAnalyticsResponse> getAuditLogsAnalytics(
        @RequestParam(defaultValue = "7d") String period,
        HttpServletRequest request
    ) {
        final Server server = RequestUtil.getRequestServer(request);
        final AuditLogsAnalyticsResponse analytics = analyticsService.getAuditLogsAnalytics(server, period);

        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/player-activity")
    public ResponseEntity<PlayerActivityResponse> getPlayerActivityAnalytics(
        @RequestParam(defaultValue = "30d") String period,
        HttpServletRequest request
    ) {
        final Server server = RequestUtil.getRequestServer(request);
        final PlayerActivityResponse analytics = analyticsService.getPlayerActivityAnalytics(server, period);

        return ResponseEntity.ok(analytics);
    }
}
