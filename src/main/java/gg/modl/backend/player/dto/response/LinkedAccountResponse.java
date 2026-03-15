package gg.modl.backend.player.dto.response;

import java.util.Date;
import org.jetbrains.annotations.Nullable;

public record LinkedAccountResponse(
    String minecraftUuid,
    String username,
    int activeBans,
    int activeMutes,
    @Nullable Date lastLinkedUpdate
) {
}
