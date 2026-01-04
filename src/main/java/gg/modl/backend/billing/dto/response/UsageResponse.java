package gg.modl.backend.billing.dto.response;

import java.util.Date;

public record UsageResponse(
        Period period,
        UsageMetric cdn,
        UsageMetric ai,
        double totalOverageCost,
        boolean usageBillingEnabled
) {
    public record Period(
            Date start,
            Date end
    ) {}

    public record UsageMetric(
            double used,
            double limit,
            double overage,
            double overageRate,
            double overageCost,
            double percentage
    ) {}
}
