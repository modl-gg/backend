package gg.modl.backend.settings.dto.request;

import gg.modl.backend.settings.data.AppealForm;
import gg.modl.backend.settings.data.OffenseLevelDurations;
import gg.modl.backend.settings.data.PunishmentDurations;
import gg.modl.backend.settings.data.PunishmentPoints;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PunishmentTypeRequest(
        @NotBlank
        @Size(
                min = RequestValidationLimits.PUNISHMENT_TYPE_NAME_MIN_LENGTH,
                max = RequestValidationLimits.PUNISHMENT_TYPE_NAME_MAX_LENGTH
        )
        String name,
        @NotBlank
        String category,
        @Valid
        PunishmentDurations durations,
        @Valid
        OffenseLevelDurations singleSeverityDurations,
        @Min(RequestValidationLimits.PUNISHMENT_POINTS_MIN)
        @Max(RequestValidationLimits.PUNISHMENT_POINTS_MAX)
        Integer singleSeverityPoints,
        @Valid
        PunishmentPoints points,
        @Min(RequestValidationLimits.PUNISHMENT_POINTS_MIN)
        @Max(RequestValidationLimits.PUNISHMENT_POINTS_MAX)
        Integer customPoints,
        @Size(max = RequestValidationLimits.PUNISHMENT_DESCRIPTION_MAX_LENGTH)
        String staffDescription,
        @Size(max = RequestValidationLimits.PUNISHMENT_DESCRIPTION_MAX_LENGTH)
        String playerDescription,
        Boolean singleSeverityPunishment,
        Boolean canBeAltBlocking,
        Boolean canBeStatWiping,
        Boolean appealable,
        @Valid
        AppealForm appealForm,
        Boolean permanentUntilSkinChange,
        Boolean permanentUntilUsernameChange
) {
    public PunishmentType toPunishmentType() {
        PunishmentType punishmentType = new PunishmentType();
        punishmentType.setName(name);
        punishmentType.setCategory(category);
        punishmentType.setDurations(durations);
        punishmentType.setSingleSeverityDurations(singleSeverityDurations);
        punishmentType.setSingleSeverityPoints(singleSeverityPoints);
        punishmentType.setPoints(points);
        punishmentType.setCustomPoints(customPoints);
        punishmentType.setStaffDescription(staffDescription);
        punishmentType.setPlayerDescription(playerDescription);
        punishmentType.setSingleSeverityPunishment(singleSeverityPunishment);
        punishmentType.setCanBeAltBlocking(canBeAltBlocking);
        punishmentType.setCanBeStatWiping(canBeStatWiping);
        punishmentType.setAppealable(appealable);
        punishmentType.setAppealForm(appealForm);
        punishmentType.setPermanentUntilSkinChange(permanentUntilSkinChange);
        punishmentType.setPermanentUntilUsernameChange(permanentUntilUsernameChange);
        return punishmentType;
    }
}
