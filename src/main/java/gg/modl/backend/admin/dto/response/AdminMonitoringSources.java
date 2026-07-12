package gg.modl.backend.admin.dto.response;

import java.util.List;

public record AdminMonitoringSources(
    List<String> sources,
    List<String> categories
) {
}
