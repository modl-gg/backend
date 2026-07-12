package gg.modl.backend.player.dto.response;

import java.util.Date;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public record AppealInfoView(
    String id,
    String type,
    @Nullable Date issued,
    @Nullable Date expires,
    boolean active,
    boolean appealable,
    String playerUuid,
    @Nullable Map<String, Object> existingAppeal,
    @Nullable Map<String, Object> appealForm
) {
}
