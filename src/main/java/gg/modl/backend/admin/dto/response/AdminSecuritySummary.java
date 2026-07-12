package gg.modl.backend.admin.dto.response;

import java.util.Date;

public record AdminSecuritySummary(
    Last24Hours last24Hours,
    long last7DaysTotal,
    Date timestamp
) {
    public record Last24Hours(
        long critical,
        long high,
        long medium
    ) {
    }
}
