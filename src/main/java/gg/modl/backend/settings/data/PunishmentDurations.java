package gg.modl.backend.settings.data;

import jakarta.validation.Valid;

public record PunishmentDurations(
    @Valid OffenseLevelDurations low,
    @Valid OffenseLevelDurations regular,
    @Valid OffenseLevelDurations severe
) {
    public DurationDetail getDuration(String severity, String offenseLevel) {
        OffenseLevelDurations severityDurations = getForSeverity(severity);
        return severityDurations != null ? severityDurations.getForOffenseLevel(offenseLevel) : null;
    }

    public OffenseLevelDurations getForSeverity(String severity) {
        return SeverityLevel.select(severity, low, regular, severe);
    }
}
