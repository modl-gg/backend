package gg.modl.backend.infrastructure.authorization;

public record PanelAccessRequest(String method, String path) {
    public boolean isReadOnly() {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
