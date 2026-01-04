package gg.modl.backend.billing.dto.response;

import java.util.Date;

public record BillingStatusResponse(
        String plan,
        String subscriptionStatus,
        Date currentPeriodStart,
        Date currentPeriodEnd
) {}
