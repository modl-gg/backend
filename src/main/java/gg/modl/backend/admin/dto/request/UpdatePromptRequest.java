package gg.modl.backend.admin.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePromptRequest(
    @NotBlank @Size(max = RequestValidationLimits.PROMPT_MAX_LENGTH) String prompt
) {}
