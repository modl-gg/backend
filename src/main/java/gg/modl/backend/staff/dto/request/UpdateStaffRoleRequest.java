package gg.modl.backend.staff.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStaffRoleRequest(
    @NotBlank @Size(max = RequestValidationLimits.STAFF_ROLE_MAX_LENGTH) String role
) {
}
