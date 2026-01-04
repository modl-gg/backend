package gg.modl.backend.audit.controller;

import gg.modl.backend.audit.dto.request.RollbackRequest;
import gg.modl.backend.audit.dto.response.PunishmentAuditResponse;
import gg.modl.backend.audit.dto.response.StaffDetailsResponse;
import gg.modl.backend.audit.dto.response.StaffPerformanceResponse;
import gg.modl.backend.audit.service.AuditService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_AUDIT)
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    @GetMapping("/staff-performance")
    public ResponseEntity<List<StaffPerformanceResponse>> getStaffPerformance(
            @RequestParam(defaultValue = "30d") String period,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<StaffPerformanceResponse> performance = auditService.getStaffPerformance(server, period);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/staff/{username}/details")
    public ResponseEntity<StaffDetailsResponse> getStaffDetails(
            @PathVariable String username,
            @RequestParam(defaultValue = "30d") String period,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        StaffDetailsResponse details = auditService.getStaffDetails(server, username, period);
        return ResponseEntity.ok(details);
    }

    @GetMapping("/punishments")
    public ResponseEntity<List<PunishmentAuditResponse>> getPunishments(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean canRollback,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<PunishmentAuditResponse> punishments = auditService.getPunishments(server, limit, canRollback);
        return ResponseEntity.ok(punishments);
    }

    @PostMapping("/punishments/{id}/rollback")
    public ResponseEntity<?> rollbackPunishment(
            @PathVariable String id,
            @RequestBody(required = false) RollbackRequest rollbackRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        // TODO: Get current user from session
        String performerUsername = "";

        try {
            String reason = rollbackRequest != null ? rollbackRequest.reason() : "Admin rollback";
            boolean success = auditService.rollbackPunishment(server, id, reason, performerUsername);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Punishment rolled back successfully"
                ));
            }
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/staff/{username}/rollback-all")
    public ResponseEntity<?> rollbackAllByStaff(
            @PathVariable String username,
            @RequestBody(required = false) RollbackRequest rollbackRequest,
            HttpServletRequest request
    ) {
        // TODO: Implement bulk rollback for all punishments by a staff member
        return ResponseEntity.status(501).body(Map.of("message", "Bulk rollback not yet implemented"));
    }

    @PostMapping("/punishments/bulk-rollback")
    public ResponseEntity<?> bulkRollback(
            @RequestBody Map<String, Object> bulkRequest,
            HttpServletRequest request
    ) {
        // TODO: Implement time-based bulk rollback
        return ResponseEntity.status(501).body(Map.of("message", "Bulk rollback not yet implemented"));
    }

    @GetMapping("/database/{table}")
    public ResponseEntity<?> getDatabaseTable(
            @PathVariable String table,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int skip,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            Map<String, Object> result = auditService.getDatabaseTable(server, table, limit, skip);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
