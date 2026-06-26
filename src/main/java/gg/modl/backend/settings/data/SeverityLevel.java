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
}
