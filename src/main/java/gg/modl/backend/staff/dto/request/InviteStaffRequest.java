package gg.modl.backend.staff.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.lang.Nullable;

public record InviteStaffRequest(
    @Nullable @Email @Size(max = RequestValidationLimits.EMAIL_MAX_LENGTH) String email,
    @Nullable @Size(max = RequestValidationLimits.STAFF_EMAILS_MAX_ENTRIES) List<@Email @Size(max = RequestValidationLimits.EMAIL_MAX_LENGTH) String> emails,
    @NotBlank @Size(max = RequestValidationLimits.STAFF_ROLE_MAX_LENGTH) String role
) {
}
