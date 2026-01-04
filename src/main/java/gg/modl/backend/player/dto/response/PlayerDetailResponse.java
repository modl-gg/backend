package gg.modl.backend.player.dto.response;

import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.UsernameEntry;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record PlayerDetailResponse(
        String id,
        String minecraftUuid,
        List<UsernameEntry> usernames,
        List<NoteEntry> notes,
        List<IPEntry> ipAddresses,
        List<PunishmentResponse> punishments,
        Map<String, Object> data,
        String social,
        String gameplay,
        int socialPoints,
        int gameplayPoints,
        @Nullable IPEntry latestIPData,
        @Nullable String lastServer,
        double playtimeHours
) {
}
