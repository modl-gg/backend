package gg.modl.backend.staff.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record CreateStaffRequest(
    @NotBlank @Email @Size(max = RequestValidationLimits.EMAIL_MAX_LENGTH) String email,
    @NotBlank @Size(max = RequestValidationLimits.STAFF_USERNAME_MAX_LENGTH) String username,
    @Nullable @Size(max = RequestValidationLimits.STAFF_ROLE_MAX_LENGTH) String role
) {
}
