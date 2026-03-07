package gg.modl.backend.staff.dto.response;

import java.util.Date;
import java.util.List;

public record MinecraftStaffSummaryResponse(
        String id,
        String username,
        String email,
        String role,
        String minecraftUuid,
        String minecraftUsername,
        List<String> permissions,
        Date lastSeen,
        long totalPlaytimeMs,
        String lastServer,
        int punishmentsIssuedCount,
        Date createdAt,
        Date updatedAt
) {
}
