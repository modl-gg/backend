package gg.modl.backend.settings.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfigureDomainRequest(
    @NotBlank(message = "Custom domain is required")
    @Size(max = RequestValidationLimits.DOMAIN_MAX_LENGTH)
    String customDomain
) {}
