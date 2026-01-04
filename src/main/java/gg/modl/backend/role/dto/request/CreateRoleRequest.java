package gg.modl.backend.role.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateRoleRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull List<String> permissions
) {
}
