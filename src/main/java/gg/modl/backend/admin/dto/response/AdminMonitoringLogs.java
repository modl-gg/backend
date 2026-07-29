package gg.modl.backend.admin.dto.response;

import gg.modl.backend.admin.data.SystemLog;
import java.util.List;

public record AdminMonitoringLogs(
    List<SystemLog> logs,
    AdminPagination pagination,
    Filters filters
) {
    public record Filters(
        String level,
        String source,
        String serverId,
        String category,
        String resolved,
        String search
    ) {
    }
}
