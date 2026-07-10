package gg.modl.backend.audit.controller;

import gg.modl.backend.audit.service.AuditService;
import gg.modl.backend.audit.service.StaffPerformanceService;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.backend.audit.dto.response.ActivePunishmentResponse;
import gg.modl.backend.audit.dto.response.PunishmentAuditResponse;
import gg.modl.backend.audit.dto.response.StaffDetailsResponse;
import gg.modl.backend.audit.dto.response.StaffPerformanceResponse;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.ActivePunishmentsAuditResponse;
import gg.modl.proto.modl.v1.AuditBulkOperationResponse;
import gg.modl.proto.modl.v1.AuditDatabaseTableResponse;
import gg.modl.proto.modl.v1.AuditRollbackResponse;
import gg.modl.proto.modl.v1.BulkPunishmentActionRequest;
import gg.modl.proto.modl.v1.DateRangeRollbackRequest;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.PunishmentAuditListResponse;
import gg.modl.proto.modl.v1.RollbackRequest;
import gg.modl.proto.modl.v1.StaffPerformanceListResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_AUDIT)
@RequiredArgsConstructor
@Validated
public class AuditController {
    private final AuditService auditService;
    private final StaffPerformanceService staffPerformanceService;
    private final PermissionService permissionService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @GetMapping("/staff-performance")
    public ResponseEntity<StaffPerformanceListResponse> getStaffPerformance(
        @RequestParam(defaultValue = "30d") String period,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<StaffPerformanceResponse> performance = staffPerformanceService.getStaffPerformance(server, period);
        return ResponseEntity.ok(AuditProtoMapper.toStaffPerformanceList(performance));
    }

    @GetMapping("/staff/{username}/details")
    public ResponseEntity<gg.modl.proto.modl.v1.StaffDetailsResponse> getStaffDetails(
        @PathVariable String username,
        @RequestParam(defaultValue = "30d") String period,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        StaffDetailsResponse details = staffPerformanceService.getStaffDetails(server, username, period);
        return ResponseEntity.ok(AuditProtoMapper.toStaffDetails(details));
    }

    @GetMapping("/punishments/active")
    public ResponseEntity<ActivePunishmentsAuditResponse> getActivePunishments(
        @RequestParam(defaultValue = "active") String status,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<ActivePunishmentResponse> punishments = auditService.getPunishmentsList(server, status);
        return ResponseEntity.ok(AuditProtoMapper.toActivePunishments(punishments));
    }

    @GetMapping("/punishments")
    public ResponseEntity<PunishmentAuditListResponse> getPunishments(
        @RequestParam(defaultValue = "50") @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) int limit,
        @RequestParam(defaultValue = "false") boolean canRollback,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<PunishmentAuditResponse> punishments = auditService.getPunishments(server, limit, canRollback);
        return ResponseEntity.ok(AuditProtoMapper.toPunishmentAuditList(punishments));
    }

    @PostMapping("/punishments/{id}/rollback")
    public ResponseEntity<AuditRollbackResponse> rollbackPunishment(
        @PathVariable String id,
        @RequestBody(required = false) RollbackRequest rollbackRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireSuperAdmin(server, request);
        String performerUsername = RequestUtil.getCurrentUsername(request);

        String reason = rollbackRequest != null && rollbackRequest.hasReason()
            ? rollbackRequest.getReason() : "Admin rollback";
        boolean success = auditService.rollbackPunishment(server, id, reason, performerUsername);

        if (success) {
            invalidateAudit(server);
            return ResponseEntity.ok(
                AuditProtoMapper.toRollbackResponse(true, "Punishment rolled back successfully"));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/staff/{username}/rollback-all")
    public ResponseEntity<AuditBulkOperationResponse> rollbackAllByStaff(
        @PathVariable String username,
        @RequestBody(required = false) RollbackRequest rollbackRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireSuperAdmin(server, request);
        String performerUsername = RequestUtil.getCurrentUsername(request);

        String reason = rollbackRequest != null && rollbackRequest.hasReason()
            ? rollbackRequest.getReason() : "Bulk rollback by admin";
        int count = auditService.rollbackAllPunishmentsByStaff(server, username, reason, performerUsername);

        invalidateAudit(server);
        return ResponseEntity.ok(AuditProtoMapper.toBulkOperationResponse(
            true, count, "Successfully rolled back " + count + " punishments"));
    }

    @PostMapping("/staff/{username}/rollback-date-range")
    public ResponseEntity<AuditBulkOperationResponse> rollbackByDateRange(
        @PathVariable String username,
        @RequestBody DateRangeRollbackRequest rollbackRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireSuperAdmin(server, request);
        String performerUsername = RequestUtil.getCurrentUsername(request);

        Date startDate = AuditProtoMapper.toDate(rollbackRequest.getStartDate());
        Date endDate = AuditProtoMapper.toDate(rollbackRequest.getEndDate());
        if (startDate == null || endDate == null) {
            throw new ValidationException("Start date and end date are required");
        }

        String reason = rollbackRequest.hasReason() ? rollbackRequest.getReason() : "Bulk rollback by admin";
        int count = auditService.rollbackPunishmentsByDateRange(
            server,
            username,
            startDate,
            endDate,
            reason,
            performerUsername
        );

        invalidateAudit(server);
        return ResponseEntity.ok(AuditProtoMapper.toBulkOperationResponse(
            true, count, "Successfully rolled back " + count + " punishments"));
    }

    @PostMapping("/punishments/bulk-pardon")
    public ResponseEntity<AuditBulkOperationResponse> bulkPardon(
        @RequestBody BulkPunishmentActionRequest actionRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireSuperAdmin(server, request);

        String performerUsername = RequestUtil.getCurrentUsername(request);
        int count = auditService.bulkPardonByType(
            server, actionRequest.getTypeOrdinalsList(), actionRequest.getReason(), performerUsername);

        invalidateAudit(server);
        return ResponseEntity.ok(AuditProtoMapper.toBulkOperationResponse(
            true, count, "Successfully pardoned " + count + " punishments"));
    }

    @PostMapping("/punishments/bulk-set-expiration")
    public ResponseEntity<AuditBulkOperationResponse> bulkSetExpiration(
        @RequestBody BulkPunishmentActionRequest actionRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireSuperAdmin(server, request);

        if (!actionRequest.hasNewDurationMs()) {
            throw new ValidationException("newDurationMs is required for set-expiration");
        }

        String performerUsername = RequestUtil.getCurrentUsername(request);
        int count = auditService.bulkSetExpirationByType(
            server, actionRequest.getTypeOrdinalsList(), actionRequest.getNewDurationMs(),
            actionRequest.getReason(), performerUsername);

        invalidateAudit(server);
        return ResponseEntity.ok(AuditProtoMapper.toBulkOperationResponse(
            true, count, "Successfully updated expiration for " + count + " punishments"));
    }

    private void requireSuperAdmin(Server server, HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (!permissionService.isSuperAdmin(server, email)) {
            throw new ForbiddenException("Only super admins can perform this action");
        }
    }

    private void invalidateAudit(Server server) {
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_AUDIT);
    }

    @GetMapping("/database/{table}")
    public ResponseEntity<AuditDatabaseTableResponse> getDatabaseTable(
        @PathVariable String table,
        @RequestParam(defaultValue = "100") @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) int limit,
        @RequestParam(defaultValue = "0") @Min(0) int skip,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireSuperAdmin(server, request);

        if (!AuditService.ALLOWED_TABLES.contains(table)) {
            throw new ValidationException("Invalid table name");
        }

        Map<String, Object> result = auditService.getDatabaseTable(server, table, limit, skip);
        return ResponseEntity.ok(AuditProtoMapper.toDatabaseTableResponse(result));
    }
}
