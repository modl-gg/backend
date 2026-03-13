package gg.modl.backend.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdatePromptRequest(
    @NotBlank String prompt
) {}
