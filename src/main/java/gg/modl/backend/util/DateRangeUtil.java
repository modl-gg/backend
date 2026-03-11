package gg.modl.backend.util;

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
}
