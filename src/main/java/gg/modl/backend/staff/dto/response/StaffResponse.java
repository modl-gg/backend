package gg.modl.backend.staff.dto.response;

import java.util.Date;

public record StaffResponse(
        String id,
        String email,
        String username,
        String role,
        String status,
        String assignedMinecraftUuid,
        String assignedMinecraftUsername,
        Date createdAt
) {
}
