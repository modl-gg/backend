package gg.modl.backend.staff.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateStaffRoleRequest(
        @NotBlank String role
) {
}
