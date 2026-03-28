package gg.modl.backend.audit.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Date;
import org.springframework.lang.Nullable;

public record DateRangeRollbackRequest(
    @NotNull Date startDate,
    @NotNull Date endDate,
    @Nullable @Size(max = RequestValidationLimits.AUDIT_ROLLBACK_REASON_MAX_LENGTH) String reason
) {
}
