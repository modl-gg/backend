package gg.modl.backend.role.dto.response;

import gg.modl.backend.role.data.Permission;

import java.util.List;
import java.util.Map;

public record PermissionsResponse(
        List<Permission> permissions,
        Map<String, String> categories
) {
}
