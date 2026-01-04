package gg.modl.backend.billing.dto.response;

import java.util.Date;

public record ResubscribeResponse(
        boolean success,
        String message,
        SubscriptionInfo subscription
) {
    public record SubscriptionInfo(
            String id,
            String status,
            Date currentPeriodEnd
    ) {}
}
