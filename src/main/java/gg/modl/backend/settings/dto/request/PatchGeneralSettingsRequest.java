package gg.modl.backend.settings.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record PatchGeneralSettingsRequest(
    @NotNull @Min(0) Long expectedVersion,
    @Nullable @Size(max = RequestValidationLimits.GENERAL_SETTINGS_DISPLAY_NAME_MAX_LENGTH) String serverDisplayName,
    @Nullable @Size(max = RequestValidationLimits.GENERAL_SETTINGS_URL_MAX_LENGTH) String discordWebhookUrl,
    @Nullable @Size(max = RequestValidationLimits.GENERAL_SETTINGS_URL_MAX_LENGTH) String homepageIconUrl,
    @Nullable @Size(max = RequestValidationLimits.GENERAL_SETTINGS_URL_MAX_LENGTH) String panelIconUrl
) {
}
