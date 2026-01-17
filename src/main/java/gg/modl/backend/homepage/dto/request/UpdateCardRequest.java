package gg.modl.backend.homepage.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateCardRequest(
        String title,
        String description,
        String icon,
        @JsonProperty("icon_color") String iconColor,
        @JsonProperty("action_type") String actionType,
        @JsonProperty("action_url") String actionUrl,
        @JsonProperty("action_button_text") String actionButtonText,
        @JsonProperty("category_id") String categoryId,
        @JsonProperty("background_color") String backgroundColor,
        @JsonProperty("is_enabled") Boolean isEnabled
) {
}
