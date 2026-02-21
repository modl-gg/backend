package gg.modl.backend.homepage.dto.request;

public record UpdateCardRequest(
        String title,
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
