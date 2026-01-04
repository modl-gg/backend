package gg.modl.backend.billing.dto.response;

public record UsageBillingSettingsResponse(
        boolean success,
        String message,
        boolean usageBillingEnabled
) {}
