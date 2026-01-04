package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record QuickResponseRequest(
        @NotBlank String actionId,
        @NotBlank String categoryId,
        Integer punishmentTypeId,
        String punishmentSeverity,
        Map<String, Object> customValues,
        String appealAction
) {
}
