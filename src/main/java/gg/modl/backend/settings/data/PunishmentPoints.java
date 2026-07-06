package gg.modl.backend.settings.data;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PunishmentPoints(
    @Min(RequestValidationLimits.PUNISHMENT_POINTS_MIN)
    @Max(RequestValidationLimits.PUNISHMENT_POINTS_MAX)
    int low,
    @Min(RequestValidationLimits.PUNISHMENT_POINTS_MIN)
    @Max(RequestValidationLimits.PUNISHMENT_POINTS_MAX)
    int regular,
    @Min(RequestValidationLimits.PUNISHMENT_POINTS_MIN)
    @Max(RequestValidationLimits.PUNISHMENT_POINTS_MAX)
    int severe
) {
    public int getForSeverity(String severity) {
        return SeverityLevel.select(severity, low, regular, severe);
    }
}
