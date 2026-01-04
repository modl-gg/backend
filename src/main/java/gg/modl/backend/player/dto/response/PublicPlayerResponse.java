package gg.modl.backend.player.dto.response;

import java.util.List;

public record PublicPlayerResponse(
        String username,
        String uuid,
        String firstJoined,
        String lastOnline,
        String playtime,
        String status,
        List<PublicWarning> warnings
) {
    public record PublicWarning(
            String type,
            String reason,
            String date,
            String by
    ) {
    }
}
