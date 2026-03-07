package gg.modl.backend.staff.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.dto.response.MinecraftStaffPermissionsResponse;
import gg.modl.backend.staff.dto.response.MinecraftStaffSummaryResponse;
import gg.modl.backend.staff.service.StaffService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_STAFF)
@RequiredArgsConstructor
public class MinecraftStaffController {
    private final StaffService staffService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllStaff(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<MinecraftStaffSummaryResponse> staffList = staffService.getMinecraftStaffSummary(server);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "staff", staffList
        ));
    }

    @GetMapping("/permissions")
    public ResponseEntity<Map<String, Object>> getStaffPermissions(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<MinecraftStaffPermissionsResponse> staffList = staffService.getMinecraftStaffPermissions(server);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "data", Map.of("staff", staffList)
        ));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<Map<String, Object>> updateStaffRole(
            @PathVariable String id,
            @RequestBody @Valid UpdateRoleRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        try {
            if (!staffService.updateMinecraftStaffRole(server, id, request.role())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", 404,
                        "message", "Staff member not found"
                ));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", 400,
                    "message", e.getMessage()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Staff role updated"
        ));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> staffDisconnect(
            @RequestBody @Valid StaffDisconnectRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);

        if (!staffService.markStaffDisconnected(server, request.minecraftUuid())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Staff member not found"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true
        ));
    }

    public record UpdateRoleRequest(
            @NotBlank String role
    ) {}

    public record StaffDisconnectRequest(
            @NotBlank String minecraftUuid,
            long sessionDurationMs
    ) {}
}
