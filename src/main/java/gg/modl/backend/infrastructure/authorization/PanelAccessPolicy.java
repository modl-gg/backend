package gg.modl.backend.infrastructure.authorization;

public interface PanelAccessPolicy {
    boolean permitsWithoutRole(PanelAccessRequest request);

    boolean permitsWithRole(PanelAccessRequest request, PanelPrincipalPermissions permissions);
}
