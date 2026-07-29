package gg.modl.backend.admin.dto.response;

import java.util.List;

public record AdminAnalyticsHistorical(
    String metric,
    String range,
    List<AdminHistoricalPoint> data
) {
}
