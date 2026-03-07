package gg.modl.backend.staff.dto.response;

import java.util.List;

public record MinecraftStaffPermissionsResponse(
        String minecraftUuid,
        String minecraftUsername,
        String staffUsername,
        String staffRole,
        List<String> permissions,
        String email
) {
}
