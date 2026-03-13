package gg.modl.backend.settings.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfigureDomainRequest(
    @NotBlank(message = "Custom domain is required")
    String customDomain
) {}
