package gg.modl.backend.log.dto.response;

import java.util.Date;

public record SystemLogResponse(
        String id,
        String description,
        String level,
        String source,
        Date created
) {}
