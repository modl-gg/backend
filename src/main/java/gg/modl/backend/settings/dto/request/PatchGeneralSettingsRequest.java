package gg.modl.backend.settings.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PatchGeneralSettingsRequest(
    @NotNull @Min(0) Long expectedVersion,
    String serverDisplayName,
    String discordWebhookUrl,
    String homepageIconUrl,
    String panelIconUrl
) {
}
