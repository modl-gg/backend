package gg.modl.backend.infrastructure.util;

import java.util.Date;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DateRangeUtil {

    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    public Date getStartDate(String period) {
        if (period == null || "all".equals(period)) {
            return null;
        }
        long now = System.currentTimeMillis();
        return switch (period) {
            case "7d" -> new Date(now - 7 * DAY_MS);
            case "90d" -> new Date(now - 90 * DAY_MS);
            case "1y" -> new Date(now - 365 * DAY_MS);
            default -> new Date(now - 30 * DAY_MS);
        };
    }

    public Date daysAgo(int days) {
        return new Date(System.currentTimeMillis() - days * DAY_MS);
    }

    public Date parseEpochMillis(String value) {
        return value == null ? null : new Date(Long.parseLong(value));
    }

    public int resolveRangeDays(String range) {
        String normalized = (range == null || range.isBlank()) ? "30d" : range;
        return switch (normalized) {
            case "7d" -> 7;
            case "90d" -> 90;
            case "365d", "1y" -> 365;
            default -> 30;
        };
    }

    public String normalizeAllFilter(String value) {
        return "all".equalsIgnoreCase(value) ? null : value;
    }
}
