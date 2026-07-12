package gg.modl.backend.role.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalString;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalStrings;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalTimestamp;

import gg.modl.backend.role.dto.response.RoleResponse;
import gg.modl.proto.modl.v1.MinecraftRole;
import gg.modl.proto.modl.v1.MinecraftRoleDetailResponse;
import gg.modl.proto.modl.v1.MinecraftRoleListResponse;
import java.util.List;

final class MinecraftRolesProtoMapper {
    private MinecraftRolesProtoMapper() {
    }

    static MinecraftRoleListResponse toRoleListResponse(List<RoleResponse> roles) {
        MinecraftRoleListResponse.Builder response = MinecraftRoleListResponse.newBuilder()
            .setStatus(200);

        if (roles != null) {
            roles.stream()
                .map(MinecraftRolesProtoMapper::toMinecraftRole)
                .forEach(response::addRoles);
        }

        return response.build();
    }

    static MinecraftRoleDetailResponse toRoleDetailResponse(RoleResponse role) {
        return MinecraftRoleDetailResponse.newBuilder()
            .setStatus(200)
            .setRole(toMinecraftRole(role))
            .build();
    }

    private static MinecraftRole toMinecraftRole(RoleResponse role) {
        MinecraftRole.Builder builder = MinecraftRole.newBuilder()
            .setIsDefault(role.isDefault())
            .setOrder(role.order());

        setOptionalString(builder::setId, role.id());
        setOptionalString(builder::setName, role.name());
        setOptionalString(builder::setDescription, role.description());
        setOptionalStrings(builder::addAllPermissions, role.permissions());
        setOptionalTimestamp(builder::setCreatedAt, role.createdAt());
        setOptionalTimestamp(builder::setUpdatedAt, role.updatedAt());

        return builder.build();
    }
}
