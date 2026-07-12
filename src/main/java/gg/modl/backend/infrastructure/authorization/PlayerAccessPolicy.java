package gg.modl.backend.infrastructure.authorization;

import gg.modl.backend.role.service.PermissionService;

public enum PlayerAccessPolicy implements PanelAccessPolicy {
    INSTANCE;

    private static final String PUNISHMENT_APPLY_PREFIX = "punishment.apply.";

    @Override
    public boolean permitsWithoutRole(PanelAccessRequest request) {
        return false;
    }

    @Override
    public boolean permitsWithRole(PanelAccessRequest request, PanelPrincipalPermissions permissions) {
        if (request.isReadOnly()) {
            return permissions.has(PermissionService.PUNISHMENT_VIEW)
                || permissions.has(PermissionService.PUNISHMENT_MODIFY)
                || permissions.hasAnyWithPrefix(PUNISHMENT_APPLY_PREFIX);
        }
        return permissions.has(PermissionService.PUNISHMENT_MODIFY);
    }
}
