package gg.modl.backend.admin.dto.request;

import org.springframework.lang.Nullable;

public record ResolveLogRequest(
    @Nullable String resolvedBy
) {}
