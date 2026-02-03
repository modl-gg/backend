package gg.modl.backend.audit.dto.request;

import java.util.Date;

public record DateRangeRollbackRequest(
        Date startDate,
        Date endDate,
        String reason
) {
}
