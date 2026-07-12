package gg.modl.backend.player.dto.response;

import java.util.List;
import java.util.Map;

public record PlayerLoginResult(
    int status,
    List<SimplePunishmentView> activePunishments,
    List<Map<String, Object>> pendingNotifications,
    List<String> pendingIpLookups,
    List<Map<String, Object>> pendingStatWipes
) {}
