package gg.modl.backend.role.dto.response;

import java.util.List;

public record RoleListResponse(
        List<RoleResponse> roles
) {
}
