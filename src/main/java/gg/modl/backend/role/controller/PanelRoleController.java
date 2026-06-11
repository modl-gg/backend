package gg.modl.backend.role.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.role.data.Permission;
import gg.modl.backend.role.dto.request.ReorderRolesRequest;
import gg.modl.backend.role.dto.request.RoleRequest;
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
        @RequestBody @Valid RoleRequest createRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String performerEmail = RequestUtil.getSessionEmail(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, performerEmail);
        String performerRoleId = getStaffRoleId(server, performerEmail);

        RoleResponse role = roleService.createRole(server, createRequest, performerRoleId, isSuperAdmin);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "Role created successfully",
            "role", role
        ));
    }

    private String getStaffRoleId(Server server, String email) {
        return staffService.getStaffByEmail(server, email)
            .map(Staff::getRoleId).orElse(null);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRole(
        @PathVariable String id,
        @RequestBody @Valid RoleRequest updateRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String performerEmail = RequestUtil.getSessionEmail(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, performerEmail);
        String performerRoleId = getStaffRoleId(server, performerEmail);

        return roleService.updateRole(server, id, updateRequest, performerRoleId, isSuperAdmin)
            .map(role -> ResponseEntity.ok(Map.of(
                "message", "Role updated successfully",
                "role", role
            )))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRole(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String performerEmail = RequestUtil.getSessionEmail(request);
        boolean isSuperAdmin = permissionService.isSuperAdmin(server, performerEmail);
        String performerRoleId = getStaffRoleId(server, performerEmail);

        boolean deleted = roleService.deleteRole(server, id, performerRoleId, isSuperAdmin);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Role deleted successfully"));
        } else {
            return ResponseEntity.notFound().build();
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
        String performerRoleId = getStaffRoleId(server, performerEmail);

        roleService.reorderRoles(server, reorderRequest, performerRoleId, isSuperAdmin);
        return ResponseEntity.ok(Map.of("message", "Role order updated successfully"));
    }
}
