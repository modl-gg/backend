package gg.modl.backend.dashboard.dto.response;

import java.util.Date;
import java.util.List;

public record ActivityItemResponse(
        String id,
        String type,
        String color,
        String title,
        Date time,
        String description,
        List<ActivityAction> actions
) {
    public record ActivityAction(
            String label,
            String link,
            boolean primary
    ) {
    }
}
