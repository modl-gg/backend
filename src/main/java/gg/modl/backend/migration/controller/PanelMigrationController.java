package gg.modl.backend.migration.controller;

import gg.modl.backend.migration.dto.MigrationStatusResponse;
import gg.modl.backend.migration.dto.StartMigrationRequest;
import gg.modl.backend.migration.service.MigrationService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_MIGRATION)
@RequiredArgsConstructor
public class PanelMigrationController {
    private final MigrationService migrationService;

    @GetMapping("/status")
    public ResponseEntity<MigrationStatusResponse> getMigrationStatus(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        MigrationStatusResponse status = migrationService.getMigrationStatus(server);
        return ResponseEntity.ok(status);
    }

    @PostMapping("/start")
    public ResponseEntity<?> startMigration(
            @RequestBody @Valid StartMigrationRequest startRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        Map<String, Object> result = migrationService.startMigration(server, startRequest.migrationType());

        if (Boolean.FALSE.equals(result.get("success"))) {
            return ResponseEntity.badRequest().body(Map.of("error", result.get("error")));
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancelMigration(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        Map<String, Object> result = migrationService.cancelMigration(server);

        if (Boolean.FALSE.equals(result.get("success"))) {
            return ResponseEntity.badRequest().body(Map.of("error", result.get("error")));
        }

        return ResponseEntity.ok(result);
    }
}
