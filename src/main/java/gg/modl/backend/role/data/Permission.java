package gg.modl.backend.role.data;

public record Permission(
    String id,
    String name,
    String description,
    String category,
    String parentId,
    boolean superAdminOnly
) {
    public Permission(String id, String name, String description, String category) {
        this(id, name, description, category, null, false);
    }

    public Permission(String id, String name, String description, String category, String parentId) {
        this(id, name, description, category, parentId, false);
    }
}
