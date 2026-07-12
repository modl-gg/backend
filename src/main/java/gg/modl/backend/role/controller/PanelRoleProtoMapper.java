package gg.modl.backend.role.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalEpochMillis;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalString;

import gg.modl.backend.role.data.Permission;
import gg.modl.backend.role.dto.request.ReorderRolesRequest;
import gg.modl.backend.role.dto.request.RoleRequest;
import gg.modl.backend.role.dto.response.RoleResponse;
import gg.modl.proto.modl.v1.PanelRoleListResponse;
import gg.modl.proto.modl.v1.PermissionsResponse;
import gg.modl.proto.modl.v1.RoleDetailResponse;
import gg.modl.proto.modl.v1.RoleMutationResponse;
import java.util.List;
import java.util.Map;

final class PanelRoleProtoMapper {
    private PanelRoleProtoMapper() {
    }

    static RoleRequest toRoleRequest(gg.modl.proto.modl.v1.RoleRequest request) {
        return new RoleRequest(
            request.getName(),
            request.getDescription(),
            List.copyOf(request.getPermissionsList())
        );
    }

    static ReorderRolesRequest toReorderRolesRequest(gg.modl.proto.modl.v1.ReorderRolesRequest request) {
        List<ReorderRolesRequest.RoleOrderItem> items = request.getRoleOrderList().stream()
            .map(item -> new ReorderRolesRequest.RoleOrderItem(item.getId(), item.getOrder()))
            .toList();
        return new ReorderRolesRequest(items);
    }

    static PanelRoleListResponse toRoleListResponse(List<RoleResponse> roles) {
        PanelRoleListResponse.Builder builder = PanelRoleListResponse.newBuilder();
        roles.stream().map(PanelRoleProtoMapper::toRoleResponse).forEach(builder::addRoles);
        return builder.build();
    }

    static RoleDetailResponse toRoleDetailResponse(RoleResponse role) {
        return RoleDetailResponse.newBuilder().setRole(toRoleResponse(role)).build();
    }

    static RoleMutationResponse toRoleMutationResponse(String message, RoleResponse role) {
        RoleMutationResponse.Builder builder = RoleMutationResponse.newBuilder().setMessage(message);
        if (role != null) {
            builder.setRole(toRoleResponse(role));
        }
        return builder.build();
    }

    static PermissionsResponse toPermissionsResponse(List<Permission> permissions, Map<String, String> categories) {
        PermissionsResponse.Builder builder = PermissionsResponse.newBuilder();
        if (permissions != null) {
            permissions.stream().map(PanelRoleProtoMapper::toPermission).forEach(builder::addPermissions);
        }
        if (categories != null) {
            builder.putAllCategories(categories);
        }
        return builder.build();
    }

    private static PermissionsResponse.Permission toPermission(Permission permission) {
        PermissionsResponse.Permission.Builder builder = PermissionsResponse.Permission.newBuilder();
        setOptionalString(builder::setId, permission.id());
        setOptionalString(builder::setName, permission.name());
        setOptionalString(builder::setDescription, permission.description());
        setOptionalString(builder::setCategory, permission.category());
        if (permission.parentId() != null) {
            builder.setParentId(permission.parentId());
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.RoleResponse toRoleResponse(RoleResponse role) {
        gg.modl.proto.modl.v1.RoleResponse.Builder builder = gg.modl.proto.modl.v1.RoleResponse.newBuilder()
            .setIsDefault(role.isDefault())
            .setOrder(role.order())
            .setUserCount(role.userCount());
        setOptionalString(builder::setId, role.id());
        setOptionalString(builder::setName, role.name());
        setOptionalString(builder::setDescription, role.description());
        if (role.permissions() != null) {
            builder.addAllPermissions(role.permissions());
        }
        setOptionalEpochMillis(builder::setCreatedAt, role.createdAt());
        setOptionalEpochMillis(builder::setUpdatedAt, role.updatedAt());
        return builder.build();
    }
}
