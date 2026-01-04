package gg.modl.backend.staff.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateStaffRequest(
        @NotBlank @Email String email,
        @NotBlank String username,
        String role
) {
}
