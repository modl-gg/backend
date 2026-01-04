package gg.modl.backend.migration.dto;

import jakarta.validation.constraints.NotBlank;

public record StartMigrationRequest(
        @NotBlank String migrationType
) {}
