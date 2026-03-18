package gg.modl.backend.staff.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
    @NotBlank @Size(max = RequestValidationLimits.STAFF_TOKEN_MAX_LENGTH) String token
) {}
