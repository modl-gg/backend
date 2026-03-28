package gg.modl.backend.audit.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record RollbackRequest(
    @Nullable @Size(max = RequestValidationLimits.AUDIT_ROLLBACK_REASON_MAX_LENGTH) String reason
) {
}
