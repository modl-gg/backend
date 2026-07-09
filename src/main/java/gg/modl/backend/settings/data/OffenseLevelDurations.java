package gg.modl.backend.settings.data;

import jakarta.validation.Valid;

public record OffenseLevelDurations(
    @Valid DurationDetail first,
    @Valid DurationDetail medium,
    @Valid DurationDetail habitual
) {
    public DurationDetail getForOffenseLevel(String offenseLevel) {
        return switch (offenseLevel.toLowerCase()) {
            case "first" -> first;
            case "medium" -> medium;
            case "habitual" -> habitual;
            default -> first;
        };
    }
}
