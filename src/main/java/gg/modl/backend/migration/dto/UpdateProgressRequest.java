package gg.modl.backend.migration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateProgressRequest(
    @NotBlank String status,
    @NotBlank String message,
    Integer recordsProcessed,
    Integer recordsSkipped,
    Integer totalRecords
) {}
