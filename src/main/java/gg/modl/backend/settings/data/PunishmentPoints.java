package gg.modl.backend.settings.data;

public record PunishmentPoints(
    int low,
    int regular,
    int severe
) {
    public int getForSeverity(String severity) {
        return switch (SeverityLevel.normalize(severity)) {
            case "low" -> low;
            case "severe" -> severe;
            default -> regular;
        };
    }
}
