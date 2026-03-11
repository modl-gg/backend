package gg.modl.backend.homepage.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCardRequest(
    @NotBlank String title,
    String description,
    String icon,
    String iconColor,
    String actionType,
    String actionUrl,
    String actionButtonText,
    String categoryId,
    String backgroundColor,
    Boolean isEnabled
) {
}
