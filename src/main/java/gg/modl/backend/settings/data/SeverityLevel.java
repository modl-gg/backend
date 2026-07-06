package gg.modl.backend.settings.data;

public final class SeverityLevel {
    private SeverityLevel() {
    }

    public static String normalize(String severity) {
        if (severity == null) {
            return "regular";
        }
        return switch (severity.toLowerCase()) {
            case "low", "lenient" -> "low";
            case "regular", "normal" -> "regular";
            case "severe", "aggravated" -> "severe";
            default -> "regular";
        };
    }

    public static <T> T select(String severity, T low, T regular, T severe) {
        return switch (normalize(severity)) {
            case "low" -> low;
            case "severe" -> severe;
            default -> regular;
        };
    }
}
