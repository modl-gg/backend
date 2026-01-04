package gg.modl.backend.role.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReorderRolesRequest(
        @NotNull List<RoleOrderItem> roleOrder
) {
    public record RoleOrderItem(
            String id,
            int order
    ) {
    }
}
