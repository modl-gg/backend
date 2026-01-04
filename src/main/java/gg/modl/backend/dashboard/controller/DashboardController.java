package gg.modl.backend.dashboard.controller;

import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.dashboard.dto.response.ActivityItemResponse;
import gg.modl.backend.dashboard.dto.response.DashboardMetricsResponse;
import gg.modl.backend.dashboard.dto.response.RecentPunishmentResponse;
import gg.modl.backend.dashboard.dto.response.RecentTicketResponse;
import gg.modl.backend.dashboard.service.DashboardService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_DASHBOARD)
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/metrics")
    public ResponseEntity<DashboardMetricsResponse> getMetrics(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        DashboardMetricsResponse metrics = dashboardService.getMetrics(server);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/recent-tickets")
    public ResponseEntity<List<RecentTicketResponse>> getRecentTickets(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<RecentTicketResponse> tickets = dashboardService.getRecentTickets(server, limit);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/recent-punishments")
    public ResponseEntity<List<RecentPunishmentResponse>> getRecentPunishments(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<RecentPunishmentResponse> punishments = dashboardService.getRecentPunishments(server, limit);
        return ResponseEntity.ok(punishments);
    }

    @GetMapping("/activity/recent")
    public ResponseEntity<?> getRecentActivity(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        AuthSessionData session = RequestUtil.getSession(request);

        if (session == null || session.getEmail() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String staffEmail = session.getEmail();
        List<ActivityItemResponse> activities = dashboardService.getRecentActivity(server, staffEmail, limit, days);
        return ResponseEntity.ok(activities);
    }
}
