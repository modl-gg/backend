package gg.modl.backend.role.data;

public record Permission(
    String id,
    String name,
    String description,
    String category,
    String parentId
) {
    public Permission(String id, String name, String description, String category) {
        this(id, name, description, category, null);
    }
}
