package gg.modl.backend.admin.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record ResolveLogRequest(
    @Nullable @Size(max = RequestValidationLimits.RESOLVE_LOG_RESOLVED_BY_MAX_LENGTH) String resolvedBy
) {}
