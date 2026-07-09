package gg.modl.backend.alert.controller;

import gg.modl.backend.alert.service.SystemAlertService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.PanelSystemAlertsResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_DASHBOARD)
@RequiredArgsConstructor
public class PanelSystemAlertController {
    private final SystemAlertService alertService;
    private final PermissionService permissionService;

    @GetMapping("/alerts")
    public ResponseEntity<PanelSystemAlertsResponse> getAlerts(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        String email = RequestUtil.getSessionEmail(request);
        boolean superAdmin = email != null && permissionService.isSuperAdmin(server, email);
        return ResponseEntity.ok(
            AlertProtoMapper.toPanelAlerts(alertService.getVisibleAlerts(superAdmin, new Date())));
    }
}
