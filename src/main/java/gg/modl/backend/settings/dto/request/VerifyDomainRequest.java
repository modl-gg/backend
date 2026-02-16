package gg.modl.backend.settings.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyDomainRequest(
        @NotBlank(message = "Domain is required")
        String domain
) {}
