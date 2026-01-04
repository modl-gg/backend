package gg.modl.backend.homepage.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCardRequest(
        @NotBlank String title,
        String description,
        String icon,
        String actionType,
        String actionValue
) {
}
