package gg.modl.backend.settings.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DurationDetail(
        int value,
        String unit,
        String type
) {
    public long toMilliseconds() {
        if (value == 0 && "permanent ban".equals(type) || "permanent mute".equals(type)) {
            return -1L;
        }

        return switch (unit) {
            case "seconds" -> value * 1000L;
            case "minutes" -> value * 60L * 1000L;
            case "hours" -> value * 60L * 60L * 1000L;
            case "days" -> value * 24L * 60L * 60L * 1000L;
            case "weeks" -> value * 7L * 24L * 60L * 60L * 1000L;
            case "months" -> value * 30L * 24L * 60L * 60L * 1000L;
            default -> 0L;
        };
    }

    public boolean isPermanent() {
        return "permanent ban".equals(type) || "permanent mute".equals(type);
    }

    public boolean isBan() {
        return "ban".equals(type) || "permanent ban".equals(type);
    }

    public boolean isMute() {
        return "mute".equals(type) || "permanent mute".equals(type);
    }
}
