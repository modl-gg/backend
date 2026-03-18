package gg.modl.backend.admin.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record UpdateServerRequest(
    @Nullable @Email @Size(max = RequestValidationLimits.EMAIL_MAX_LENGTH) String adminEmail,
    @Nullable Boolean emailVerified,
    @Nullable @Size(max = RequestValidationLimits.ADMIN_PROVISIONING_STATUS_MAX_LENGTH) String provisioningStatus,
    @Nullable @Size(max = RequestValidationLimits.ADMIN_PROVISIONING_NOTES_MAX_LENGTH) String provisioningNotes,
    @Nullable @Size(max = RequestValidationLimits.ADMIN_PLAN_MAX_LENGTH) String plan,
    @Nullable @Size(max = RequestValidationLimits.ADMIN_SUBSCRIPTION_STATUS_MAX_LENGTH) String subscriptionStatus,
    @Nullable @Size(max = RequestValidationLimits.TIMESTAMP_MAX_LENGTH) String lastActivityAt
) {}
