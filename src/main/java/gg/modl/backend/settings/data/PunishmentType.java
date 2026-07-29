package gg.modl.backend.settings.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PunishmentType {
    private Integer id;
    private String name;
    private String category;
    private Boolean customizable;
    private Integer ordinal;

    @Valid
    private PunishmentDurations durations;

    private Boolean singleSeverityPunishment;
    @Valid
    private OffenseLevelDurations singleSeverityDurations;
    @Min(RequestValidationLimits.PUNISHMENT_POINTS_MIN)
    @Max(RequestValidationLimits.PUNISHMENT_POINTS_MAX)
    private Integer singleSeverityPoints;

    @Valid
    private PunishmentPoints points;
    @Min(RequestValidationLimits.PUNISHMENT_POINTS_MIN)
    @Max(RequestValidationLimits.PUNISHMENT_POINTS_MAX)
    private Integer customPoints;

    private String staffDescription;
    private String playerDescription;

    private Boolean canBeAltBlocking;
    private Boolean canBeStatWiping;
    private Boolean appealable;

    private AppealForm appealForm;

    private Boolean permanentUntilSkinChange;
    private Boolean permanentUntilUsernameChange;

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
        PunishmentCategory coreCategory = PunishmentCategory.fromCoreOrdinal(ordinal);
        if (coreCategory != null) {
            return coreCategory == PunishmentCategory.BAN;
        }
        return category != null && category.toLowerCase().contains("ban");
    }

    public boolean isMute() {
        PunishmentCategory coreCategory = PunishmentCategory.fromCoreOrdinal(ordinal);
        if (coreCategory != null) {
            return coreCategory == PunishmentCategory.MUTE;
        }
        return category != null && category.toLowerCase().contains("mute");
    }

    public boolean isKick() {
        PunishmentCategory coreCategory = PunishmentCategory.fromCoreOrdinal(ordinal);
        if (coreCategory != null) {
            return coreCategory == PunishmentCategory.KICK;
        }
        return category != null && category.toLowerCase().contains("kick");
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
        if (isSingleSeverityPunishment() && singleSeverityDurations != null) {
            DurationDetail detail = singleSeverityDurations.getForOffenseLevel(offenseLevel);
            return detail != null ? detail.toMilliseconds() : 0L;
        }
        if (durations != null) {
            DurationDetail detail = durations.getDuration(severity, offenseLevel);
            return detail != null ? detail.toMilliseconds() : 0L;
        }
        return 0L;
    }

    public boolean isSingleSeverityPunishment() {
        return singleSeverityPunishment != null && singleSeverityPunishment;
    }

    public DurationDetail getDurationDetail(String severity, String offenseLevel) {
        if (isSingleSeverityPunishment() && singleSeverityDurations != null) {
            return singleSeverityDurations.getForOffenseLevel(offenseLevel);
        }
        if (durations != null) {
            return durations.getDuration(severity, offenseLevel);
        }
        return null;
    }

    public boolean isCustomizable() {
        return customizable != null && customizable;
    }

    public boolean isCanBeAltBlocking() {
        return canBeAltBlocking != null && canBeAltBlocking;
    }

    public boolean isCanBeStatWiping() {
        return canBeStatWiping != null && canBeStatWiping;
    }

    public boolean isAppealable() {
        return appealable != null && appealable;
    }

    public boolean isPermanentUntilSkinChange() {
        return permanentUntilSkinChange != null && permanentUntilSkinChange;
    }

    public boolean isPermanentUntilUsernameChange() {
        return permanentUntilUsernameChange != null && permanentUntilUsernameChange;
    }
}
