package gg.modl.backend.alert.dto.request;

import gg.modl.backend.alert.data.SystemAlertAudience;
import gg.modl.backend.alert.data.SystemAlertSeverity;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import java.util.Date;

public record UpdateSystemAlertRequest(
    @Size(max = RequestValidationLimits.MAINTENANCE_MESSAGE_MAX_LENGTH) String message,
    SystemAlertSeverity severity,
    SystemAlertAudience audience,
    @Future Date expiresAt
) {}
