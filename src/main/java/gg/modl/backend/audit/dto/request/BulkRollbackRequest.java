package gg.modl.backend.audit.dto.request;

import org.springframework.lang.Nullable;

import java.util.List;

public record BulkRollbackRequest(
        @Nullable
        List<String> punishmentIds,

        @Nullable
        String reason
) {}
