package gg.modl.backend.migration.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProgressRequest(
        @NotBlank String status,
        @NotBlank String message,
        Integer recordsProcessed,
        Integer recordsSkipped,
        Integer totalRecords
) {}
