package gg.modl.backend.infrastructure.authorization;

import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import org.jetbrains.annotations.Nullable;

public record PanelPrincipalPermissions(Server server, @Nullable String roleId, boolean superAdmin, PermissionService permissionService) {
    public boolean has(String permission) {
        return permissionService.hasPermission(server, roleId, permission);
    }

    public boolean hasAnyWithPrefix(String prefix) {
        return permissionService.hasAnyPermissionWithPrefix(server, roleId, prefix);
    }
}
