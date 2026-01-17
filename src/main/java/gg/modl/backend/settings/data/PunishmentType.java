package gg.modl.backend.settings.data;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PunishmentType {
    @JsonAlias("_id")
    private int id;
    private String name;
    private String category;
    private boolean customizable;
    private int ordinal;

    private PunishmentDurations durations;

    private boolean singleSeverityPunishment;
    private OffenseLevelDurations singleSeverityDurations;
    private Integer singleSeverityPoints;

    private PunishmentPoints points;
    private Integer customPoints;

    private String staffDescription;
    private String playerDescription;

    private boolean canBeAltBlocking;
    private boolean canBeStatWiping;
    private boolean appealable;

    private boolean permanentUntilSkinChange;
    private boolean permanentUntilUsernameChange;

    public boolean isSocial() {
        return "Social".equalsIgnoreCase(category);
    }

    public boolean isGameplay() {
        return "Gameplay".equalsIgnoreCase(category);
    }

    public boolean isAdministrative() {
        return "Administrative".equalsIgnoreCase(category);
    }

    public boolean isBan() {
        return ordinal == 2 || ordinal == 3 || ordinal == 4 || ordinal == 5;
    }

    public boolean isMute() {
        return ordinal == 1;
    }

    public boolean isKick() {
        return ordinal == 0;
    }

    public int getPointsForSeverity(String severity) {
        if (customPoints != null) {
            return customPoints;
        }
        if (singleSeverityPoints != null) {
            return singleSeverityPoints;
        }
        if (points != null) {
            return points.getForSeverity(severity);
        }
        return 0;
    }

    public long getDurationMillis(String severity, String offenseLevel) {
        if (singleSeverityPunishment && singleSeverityDurations != null) {
            DurationDetail detail = singleSeverityDurations.getForOffenseLevel(offenseLevel);
            return detail != null ? detail.toMilliseconds() : 0L;
        }
        if (durations != null) {
            DurationDetail detail = durations.getDuration(severity, offenseLevel);
            return detail != null ? detail.toMilliseconds() : 0L;
        }
        return 0L;
    }

    public DurationDetail getDurationDetail(String severity, String offenseLevel) {
        if (singleSeverityPunishment && singleSeverityDurations != null) {
            return singleSeverityDurations.getForOffenseLevel(offenseLevel);
        }
        if (durations != null) {
            return durations.getDuration(severity, offenseLevel);
        }
        return null;
    }
}
