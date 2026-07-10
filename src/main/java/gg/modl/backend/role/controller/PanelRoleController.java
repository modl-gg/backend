package gg.modl.backend.role.controller;

import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.log.service.PanelActionAuditor;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.role.data.Permission;
import gg.modl.backend.role.dto.request.ReorderRolesRequest;
import gg.modl.backend.role.dto.request.RoleRequest;
import gg.modl.backend.role.dto.response.RoleResponse;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.role.service.RoleService;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.PanelRoleListResponse;
import gg.modl.proto.modl.v1.PermissionsResponse;
import gg.modl.proto.modl.v1.RoleDetailResponse;
import gg.modl.proto.modl.v1.RoleMutationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final RoleAuthorization roleAuthorization;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final PanelActionAuditor panelActionAuditor;
    private final Validator validator;

    @GetMapping
    public ResponseEntity<PanelRoleListResponse> getAllRoles(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<RoleResponse> roles = roleService.getAllRoles(server);
        return ResponseEntity.ok(PanelRoleProtoMapper.toRoleListResponse(roles));
    }

    @GetMapping("/permissions")
    public ResponseEntity<PermissionsResponse> getPermissions(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<Permission> permissions = permissionService.getAllPermissions(server);
        Map<String, String> categories = permissionService.getPermissionCategories();
        return ResponseEntity.ok(PanelRoleProtoMapper.toPermissionsResponse(permissions, categories));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDetailResponse> getRoleById(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return roleService.getRoleById(server, id)
            .map(PanelRoleProtoMapper::toRoleDetailResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RoleMutationResponse> createRole(
        @RequestBody gg.modl.proto.modl.v1.RoleRequest createRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String performerEmail = RequestUtil.getSessionEmail(request);
        RoleAuthorization.PerformerAuthority performer = roleAuthorization.panelPerformer(server, performerEmail);

        RoleRequest mappedRequest = PanelRoleProtoMapper.toRoleRequest(createRequest);
        validate(mappedRequest);
        RoleResponse role = roleService.createRole(server, mappedRequest, performer);
        invalidateRoles(server, role.id());
        panelActionAuditor.recordStaffAction(server, performerEmail, "Created staff role: " + role.name());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PanelRoleProtoMapper.toRoleMutationResponse("Role created successfully", role));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleMutationResponse> updateRole(
        @PathVariable String id,
        @RequestBody gg.modl.proto.modl.v1.RoleRequest updateRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        RoleAuthorization.PerformerAuthority performer =
            roleAuthorization.panelPerformer(server, RequestUtil.getSessionEmail(request));

        RoleRequest mappedRequest = PanelRoleProtoMapper.toRoleRequest(updateRequest);
        validate(mappedRequest);
        return roleService.updateRole(server, id, mappedRequest, performer)
            .map(role -> {
                invalidateRoles(server, role.id());
                return ResponseEntity.ok(
                    PanelRoleProtoMapper.toRoleMutationResponse("Role updated successfully", role));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<RoleMutationResponse> deleteRole(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        RoleAuthorization.PerformerAuthority performer =
            roleAuthorization.panelPerformer(server, RequestUtil.getSessionEmail(request));

        boolean deleted = roleService.deleteRole(server, id, performer);
        if (deleted) {
            invalidateRoles(server, id);
            return ResponseEntity.ok(
                PanelRoleProtoMapper.toRoleMutationResponse("Role deleted successfully", null));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/reorder")
    public ResponseEntity<RoleMutationResponse> reorderRoles(
        @RequestBody gg.modl.proto.modl.v1.ReorderRolesRequest reorderRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        RoleAuthorization.PerformerAuthority performer =
            roleAuthorization.panelPerformer(server, RequestUtil.getSessionEmail(request));

        ReorderRolesRequest mappedRequest = PanelRoleProtoMapper.toReorderRolesRequest(reorderRequest);
        roleService.reorderRoles(server, mappedRequest, performer);
        invalidateRoles(server, null);
        return ResponseEntity.ok(
            PanelRoleProtoMapper.toRoleMutationResponse("Role order updated successfully", null));
    }

    private void invalidateRoles(Server server, String roleId) {
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_ROLES, roleId);
    }

    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ValidationException(violations.iterator().next().getMessage());
        }
    }
}
