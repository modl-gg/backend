package gg.modl.backend.role.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.data.Permission;
import gg.modl.backend.role.dto.request.CreateRoleRequest;
import gg.modl.backend.role.dto.request.ReorderRolesRequest;
import gg.modl.backend.role.dto.request.UpdateRoleRequest;
import gg.modl.backend.role.dto.response.PermissionsResponse;
import gg.modl.backend.role.dto.response.RoleListResponse;
import gg.modl.backend.role.dto.response.RoleResponse;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleService;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_ROLES)
@RequiredArgsConstructor
public class PanelRoleController {
    private final RoleService roleService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<RoleListResponse> getAllRoles(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<RoleResponse> roles = roleService.getAllRoles(server);
        return ResponseEntity.ok(new RoleListResponse(roles));
    }

    @GetMapping("/permissions")
    public ResponseEntity<PermissionsResponse> getPermissions(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<Permission> permissions = permissionService.getAllPermissions(server);
        Map<String, String> categories = permissionService.getPermissionCategories();
        return ResponseEntity.ok(new PermissionsResponse(permissions, categories));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, RoleResponse>> getRoleById(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return roleService.getRoleById(server, id)
                .map(role -> ResponseEntity.ok(Map.of("role", role)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createRole(
            @RequestBody @Valid CreateRoleRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            RoleResponse role = roleService.createRole(server, createRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Role created successfully",
                    "role", role
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRole(
            @PathVariable String id,
            @RequestBody @Valid UpdateRoleRequest updateRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            return roleService.updateRole(server, id, updateRequest)
                    .map(role -> ResponseEntity.ok(Map.of(
                            "message", "Role updated successfully",
                            "role", role
                    )))
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRole(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            boolean deleted = roleService.deleteRole(server, id);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Role deleted successfully"));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", e.getMessage(),
                    "message", "Please reassign all staff members to a different role before deleting this role."
            ));
        }
    }

    @PostMapping("/reorder")
    public ResponseEntity<?> reorderRoles(
            @RequestBody @Valid ReorderRolesRequest reorderRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        roleService.reorderRoles(server, reorderRequest);
        return ResponseEntity.ok(Map.of("message", "Role order updated successfully"));
    }
}
