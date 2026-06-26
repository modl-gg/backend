package gg.modl.backend.settings.data;

public record PunishmentDurations(
    OffenseLevelDurations low,
    OffenseLevelDurations regular,
    OffenseLevelDurations severe
) {
    public DurationDetail getDuration(String severity, String offenseLevel) {
        OffenseLevelDurations severityDurations = getForSeverity(severity);
        return severityDurations != null ? severityDurations.getForOffenseLevel(offenseLevel) : null;
    }

    public OffenseLevelDurations getForSeverity(String severity) {
        return switch (SeverityLevel.normalize(severity)) {
            case "low" -> low;
            case "severe" -> severe;
            default -> regular;
        };
    }
}
