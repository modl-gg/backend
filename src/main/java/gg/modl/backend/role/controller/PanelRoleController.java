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
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_ROLES)
@RequiredArgsConstructor
public class PanelRoleController {
    private final RoleService roleService;
    private final PermissionService permissionService;
    private final StaffService staffService;

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
        String performerEmail = RequestUtil.getSessionEmail(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, performerEmail);
        String performerRoleName = getStaffRole(server, performerEmail);

        try {
            RoleResponse role = roleService.createRole(server, createRequest, performerRoleName, isSuperAdmin);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Role created successfully",
                "role", role
            ));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("authority")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String getStaffRole(Server server, String email) {
        return staffService.getStaffByEmail(server, email)
            .map(Staff::getRole).orElse(null);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRole(
        @PathVariable String id,
        @RequestBody @Valid UpdateRoleRequest updateRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String performerEmail = RequestUtil.getSessionEmail(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, performerEmail);
        String performerRoleName = getStaffRole(server, performerEmail);

        try {
            return roleService.updateRole(server, id, updateRequest, performerRoleName, isSuperAdmin)
                .map(role -> ResponseEntity.ok(Map.of(
                    "message", "Role updated successfully",
                    "role", role
                )))
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("authority")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRole(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String performerEmail = RequestUtil.getSessionEmail(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, performerEmail);
        String performerRoleName = getStaffRole(server, performerEmail);

        try {
            boolean deleted = roleService.deleteRole(server, id, performerRoleName, isSuperAdmin);
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
        String performerEmail = RequestUtil.getSessionEmail(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, performerEmail);
        String performerRoleName = getStaffRole(server, performerEmail);

        try {
            roleService.reorderRoles(server, reorderRequest, performerRoleName, isSuperAdmin);
            return ResponseEntity.ok(Map.of("message", "Role order updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }
}
