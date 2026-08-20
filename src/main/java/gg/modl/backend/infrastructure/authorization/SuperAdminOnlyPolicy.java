package gg.modl.backend.infrastructure.authorization;

public enum SuperAdminOnlyPolicy implements PanelAccessPolicy {
    INSTANCE;

    @Override
    public boolean permitsWithoutRole(PanelAccessRequest request) {
        return false;
    }

    @Override
    public boolean permitsWithRole(PanelAccessRequest request, PanelPrincipalPermissions permissions) {
        return permissions.superAdmin();
    }
}
