package gg.modl.backend.admin.dto.response;

import java.util.List;

public record AdminAnalyticsActivity(
    long totalPlayers,
    long totalServers,
    List<ActivityPoint> data
) {
    public record ActivityPoint(
        String date,
        long activeServers,
        int onlinePlayers
    ) {
    }
}
