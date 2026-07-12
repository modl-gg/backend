package gg.modl.backend.admin.dto.response;

import gg.modl.backend.admin.data.SecurityEvent;
import java.util.List;

public record AdminSecurityEvents(
    List<SecurityEvent> events,
    AdminPagination pagination
) {
}
