package gg.modl.backend.staff.dto.request;

import jakarta.validation.constraints.Email;

public record UpdateStaffRequest(
        @Email String email,
        String role
) {
}
