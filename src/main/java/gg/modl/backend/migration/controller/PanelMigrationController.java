package gg.modl.backend.migration.controller;

import gg.modl.backend.infrastructure.authorization.RequiresPanelPermission;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.migration.service.MigrationService;
import gg.modl.backend.migration.service.MigrationService.CooldownState;
import gg.modl.backend.migration.service.MigrationService.MigrationOperationResult;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.MigrationOperationResponse;
import gg.modl.proto.modl.v1.MigrationStatusResponse;
import gg.modl.proto.modl.v1.StartMigrationRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_MIGRATION)
@RequiresPanelPermission(view = "admin.settings.view.migration", modify = "admin.settings.modify.migration")
@RequiredArgsConstructor
public class PanelMigrationController {
    private final MigrationService migrationService;
    private final MigrationProtoMapper mapper;

    @GetMapping("/status")
    public ResponseEntity<MigrationStatusResponse> getMigrationStatus(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        CooldownState cooldown = migrationService.checkCooldown(server);
        return ResponseEntity.ok(mapper.toStatusResponse(
            migrationService.getLatestMigration(server).orElse(null),
            cooldown.onCooldown(),
            cooldown.remainingTime()));
    }

    @PostMapping("/start")
    public ResponseEntity<MigrationOperationResponse> startMigration(
        @RequestBody StartMigrationRequest startRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        MigrationOperationResult result = migrationService.startMigration(server, startRequest.getMigrationType());
        return toResponse(result);
    }

    @PostMapping("/cancel")
    public ResponseEntity<MigrationOperationResponse> cancelMigration(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        MigrationOperationResult result = migrationService.cancelMigration(server);
        return toResponse(result);
    }

    private ResponseEntity<MigrationOperationResponse> toResponse(MigrationOperationResult result) {
        MigrationOperationResponse response = mapper.toOperationResponse(result);
        if (!result.success()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
}
