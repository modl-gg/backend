package gg.modl.backend.staff.dto.response;

import java.util.List;

public record MinecraftStaffPermissionsResponse(
        String minecraftUuid,
        String minecraftUsername,
        String staffUsername,
        String staffId,
        String staffRole,
        List<String> permissions,
        String email
) {
}
