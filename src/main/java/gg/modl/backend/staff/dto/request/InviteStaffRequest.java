package gg.modl.backend.staff.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record InviteStaffRequest(
        String email,
        List<String> emails,
        @NotBlank String role
) {
}
