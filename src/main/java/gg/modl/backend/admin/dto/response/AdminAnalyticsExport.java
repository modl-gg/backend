package gg.modl.backend.admin.dto.response;

public record AdminAnalyticsExport(
    String exportDate,
    String range,
    long servers,
    long users,
    long tickets
) {
}
