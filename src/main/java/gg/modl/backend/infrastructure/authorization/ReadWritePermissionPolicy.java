package gg.modl.backend.infrastructure.authorization;

public record ReadWritePermissionPolicy(String viewPermission, String modifyPermission) implements PanelAccessPolicy {
    @Override
    public boolean permitsWithoutRole(PanelAccessRequest request) {
        return false;
    }

    @Override
    public boolean permitsWithRole(PanelAccessRequest request, PanelPrincipalPermissions permissions) {
        return permissions.has(request.isReadOnly() ? viewPermission : modifyPermission);
    }
}
