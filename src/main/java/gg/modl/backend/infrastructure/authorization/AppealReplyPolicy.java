package gg.modl.backend.infrastructure.authorization;

import gg.modl.backend.role.service.PermissionService;

public enum AppealReplyPolicy implements PanelAccessPolicy {
    INSTANCE;

    @Override
    public boolean permitsWithoutRole(PanelAccessRequest request) {
        return false;
    }

    @Override
    public boolean permitsWithRole(PanelAccessRequest request, PanelPrincipalPermissions permissions) {
        if (request.isReadOnly()) {
            return permissions.has(PermissionService.TICKET_VIEW_ALL);
        }
        return permissions.has(PermissionService.APPEAL_MODIFY)
            || permissions.has(PermissionService.TICKET_REPLY_ALL);
    }
}
