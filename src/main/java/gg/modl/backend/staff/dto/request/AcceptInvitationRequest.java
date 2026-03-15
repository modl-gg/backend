package gg.modl.backend.staff.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AcceptInvitationRequest(@NotBlank String token) {}
