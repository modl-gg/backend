package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddTagRequest(
        @NotBlank String tag,
        String staffName
) {
}
