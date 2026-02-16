package gg.modl.backend.admin.dto.request;

import jakarta.validation.constraints.NotNull;

public record TogglePm2Request(
        @NotNull Boolean enabled
) {}
