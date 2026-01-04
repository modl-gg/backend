package gg.modl.backend.settings.data;

public record PunishmentDurations(
        OffenseLevelDurations low,
        OffenseLevelDurations regular,
        OffenseLevelDurations severe
) {
    public OffenseLevelDurations getForSeverity(String severity) {
        return switch (severity.toLowerCase()) {
            case "low" -> low;
            case "regular" -> regular;
            case "severe" -> severe;
            default -> regular;
        };
    }

    public DurationDetail getDuration(String severity, String offenseLevel) {
        OffenseLevelDurations severityDurations = getForSeverity(severity);
        return severityDurations != null ? severityDurations.getForOffenseLevel(offenseLevel) : null;
    }
}
