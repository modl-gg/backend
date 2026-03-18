package gg.modl.backend.role.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateRoleRequest(
    @NotBlank @Size(max = RequestValidationLimits.ROLE_NAME_MAX_LENGTH) String name,
    @NotBlank @Size(max = RequestValidationLimits.ROLE_DESCRIPTION_MAX_LENGTH) String description,
    @NotNull @Size(max = RequestValidationLimits.ROLE_PERMISSIONS_MAX_ENTRIES) List<@NotBlank @Size(max = RequestValidationLimits.ROLE_PERMISSION_MAX_LENGTH) String> permissions
) {
}
