package gg.modl.backend.admin.dto.response;

public record AdminAnalyticsUsage(
    long monthlyActiveServers,
    long storage,
    double storagePercent,
    long apiCalls,
    long databaseQueries
) {
}
