package gg.modl.backend.audit.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.lang.Nullable;

public record BulkPunishmentActionRequest(
    @NotNull @Size(min = 1) List<Integer> typeOrdinals,
    @NotNull @Size(min = 1, max = RequestValidationLimits.AUDIT_ROLLBACK_REASON_MAX_LENGTH) String reason,
    @Nullable Long newDurationMs
) {}
