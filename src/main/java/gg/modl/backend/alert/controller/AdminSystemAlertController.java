package gg.modl.backend.alert.controller;

import gg.modl.backend.alert.data.SystemAlert;
import gg.modl.backend.alert.service.SystemAlertService;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.proto.modl.v1.AdminSystemAlertResponse;
import gg.modl.proto.modl.v1.AdminSystemAlertsResponse;
import gg.modl.proto.modl.v1.CreateSystemAlertRequest;
import gg.modl.proto.modl.v1.UpdateSystemAlertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_ALERTS)
@RequiredArgsConstructor
public class AdminSystemAlertController {
    private final SystemAlertService alertService;

    @GetMapping
    public ResponseEntity<AdminSystemAlertsResponse> getAlerts() {
        return ResponseEntity.ok(AlertProtoMapper.toAdminAlerts(alertService.getAllAlerts()));
    }

    @PostMapping
    public ResponseEntity<AdminSystemAlertResponse> createAlert(@RequestBody CreateSystemAlertRequest request) {
        SystemAlert alert = alertService.createAlert(
            request.getMessage(),
            AlertProtoMapper.parseSeverity(request.getSeverity()),
            AlertProtoMapper.parseAudience(request.getAudience()),
            AlertProtoMapper.toExpiresAt(request.getExpiresAt()),
            getAdminEmail()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(AlertProtoMapper.toAdminAlert(alert));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminSystemAlertResponse> updateAlert(
        @PathVariable String id,
        @RequestBody UpdateSystemAlertRequest request
    ) {
        SystemAlert alert = alertService.updateAlert(
            id,
            request.hasMessage() ? request.getMessage() : null,
            request.hasSeverity() ? AlertProtoMapper.parseSeverity(request.getSeverity()) : null,
            request.hasAudience() ? AlertProtoMapper.parseAudience(request.getAudience()) : null,
            request.hasExpiresAt() ? AlertProtoMapper.toExpiresAt(request.getExpiresAt()) : null,
            getAdminEmail()
        ).orElseThrow(() -> new ResourceNotFoundException("Alert not found"));
        return ResponseEntity.ok(AlertProtoMapper.toAdminAlert(alert));
    }

    private String getAdminEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "unknown";
        }
        return authentication.getName();
    }
}
