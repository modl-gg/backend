package gg.modl.backend.player.dto.response;

import java.util.Date;

public record PlayerSearchResult(
        String uuid,
        String username,
        String status,
        Date lastOnline
) {
}
