package gg.modl.backend.homepage.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record CreateCardRequest(
    @NotBlank @Size(max = RequestValidationLimits.HOMEPAGE_CARD_TITLE_MAX_LENGTH) String title,
    @Nullable @Size(max = RequestValidationLimits.HOMEPAGE_CARD_DESCRIPTION_MAX_LENGTH) String description,
    @Nullable @Size(max = RequestValidationLimits.HOMEPAGE_CARD_ICON_MAX_LENGTH) String icon,
    @Nullable @Size(max = RequestValidationLimits.HOMEPAGE_CARD_COLOR_MAX_LENGTH) String iconColor,
    @Nullable @Size(max = RequestValidationLimits.HOMEPAGE_CARD_ACTION_TYPE_MAX_LENGTH) String actionType,
    @Nullable @Size(max = RequestValidationLimits.HOMEPAGE_CARD_URL_MAX_LENGTH) String actionUrl,
    @Nullable @Size(max = RequestValidationLimits.HOMEPAGE_CARD_BUTTON_TEXT_MAX_LENGTH) String actionButtonText,
    @Nullable @Size(max = RequestValidationLimits.ID_MAX_LENGTH) String categoryId,
    @Nullable @Size(max = RequestValidationLimits.HOMEPAGE_CARD_COLOR_MAX_LENGTH) String backgroundColor,
    @Nullable Boolean isEnabled
) {
}
