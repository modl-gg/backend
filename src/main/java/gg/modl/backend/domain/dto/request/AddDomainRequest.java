package gg.modl.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddDomainRequest(
        @NotBlank String domain
) {
}
