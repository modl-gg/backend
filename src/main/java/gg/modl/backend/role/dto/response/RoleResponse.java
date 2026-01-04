package gg.modl.backend.role.dto.response;

import java.util.Date;
import java.util.List;

public record RoleResponse(
        String id,
        String name,
        String description,
        List<String> permissions,
        boolean isDefault,
        int order,
        int userCount,
        Date createdAt,
        Date updatedAt
) {
}
