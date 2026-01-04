package gg.modl.backend.player.dto.response;

import org.jetbrains.annotations.Nullable;

import java.util.Date;

public record LinkedAccountResponse(
        String minecraftUuid,
        String username,
        int activeBans,
        int activeMutes,
        @Nullable Date lastLinkedUpdate
) {
}
