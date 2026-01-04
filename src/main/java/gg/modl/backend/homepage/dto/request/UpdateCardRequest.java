package gg.modl.backend.homepage.dto.request;

public record UpdateCardRequest(
        String title,
        String description,
        String icon,
        String actionType,
        String actionValue,
        Boolean isVisible
) {
}
