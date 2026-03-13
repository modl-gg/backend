package gg.modl.backend.admin.dto.request;

import jakarta.validation.constraints.Email;
import org.springframework.lang.Nullable;

public record UpdateServerRequest(
    @Nullable @Email String adminEmail,
    @Nullable Boolean emailVerified,
    @Nullable String provisioningStatus,
    @Nullable String provisioningNotes,
    @Nullable String plan,
    @Nullable String subscriptionStatus,
    @Nullable String lastActivityAt
) {}
