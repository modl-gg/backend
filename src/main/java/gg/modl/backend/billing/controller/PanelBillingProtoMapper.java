package gg.modl.backend.billing.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalBoolean;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalLong;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.billing.dto.response.BillingStatusResponse;
import gg.modl.backend.billing.dto.response.CancelResponse;
import gg.modl.backend.billing.dto.response.CheckoutSessionResponse;
import gg.modl.backend.billing.dto.response.PortalSessionResponse;
import gg.modl.backend.billing.dto.response.ResubscribeResponse;
import gg.modl.backend.billing.dto.response.UsageBillingSettingsResponse;
import gg.modl.backend.billing.dto.response.UsageResponse;

final class PanelBillingProtoMapper {

    private PanelBillingProtoMapper() {
    }

    static gg.modl.proto.modl.v1.CheckoutSessionResponse toCheckoutSessionResponse(CheckoutSessionResponse model) {
        return gg.modl.proto.modl.v1.CheckoutSessionResponse.newBuilder()
            .setSessionId(stringValue(model.sessionId()))
            .setUrl(stringValue(model.url()))
            .build();
    }

    static gg.modl.proto.modl.v1.PortalSessionResponse toPortalSessionResponse(PortalSessionResponse model) {
        return gg.modl.proto.modl.v1.PortalSessionResponse.newBuilder()
            .setUrl(stringValue(model.url()))
            .build();
    }

    static gg.modl.proto.modl.v1.CancelResponse toCancelResponse(CancelResponse model) {
        gg.modl.proto.modl.v1.CancelResponse.Builder builder = gg.modl.proto.modl.v1.CancelResponse.newBuilder()
            .setSuccess(model.success())
            .setMessage(stringValue(model.message()));
        if (model.cancelsAt() != null) {
            builder.setCancelsAt(toTimestamp(model.cancelsAt()));
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.ResubscribeResponse toResubscribeResponse(ResubscribeResponse model) {
        gg.modl.proto.modl.v1.ResubscribeResponse.Builder builder = gg.modl.proto.modl.v1.ResubscribeResponse.newBuilder()
            .setSuccess(model.success())
            .setMessage(stringValue(model.message()));
        ResubscribeResponse.SubscriptionInfo subscription = model.subscription();
        if (subscription != null) {
            gg.modl.proto.modl.v1.ResubscribeResponse.SubscriptionInfo.Builder subscriptionBuilder =
                gg.modl.proto.modl.v1.ResubscribeResponse.SubscriptionInfo.newBuilder()
                    .setId(stringValue(subscription.id()))
                    .setStatus(stringValue(subscription.status()));
            if (subscription.currentPeriodEnd() != null) {
                subscriptionBuilder.setCurrentPeriodEnd(toTimestamp(subscription.currentPeriodEnd()));
            }
            builder.setSubscription(subscriptionBuilder);
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.BillingStatusResponse toBillingStatusResponse(BillingStatusResponse model) {
        gg.modl.proto.modl.v1.BillingStatusResponse.Builder builder = gg.modl.proto.modl.v1.BillingStatusResponse.newBuilder()
            .setPlan(stringValue(model.plan()))
            .setSubscriptionStatus(stringValue(model.subscriptionStatus()));
        if (model.currentPeriodStart() != null) {
            builder.setCurrentPeriodStart(toTimestamp(model.currentPeriodStart()));
        }
        if (model.currentPeriodEnd() != null) {
            builder.setCurrentPeriodEnd(toTimestamp(model.currentPeriodEnd()));
        }
        setOptionalBoolean(builder::setCustomDomainGrandfathered, model.customDomainGrandfathered());
        setOptionalLong(builder::setMaxStorageLimitBytes, model.maxStorageLimitBytes());
        setOptionalLong(builder::setMaxAiOverageRequests, model.maxAiOverageRequests());
        return builder.build();
    }

    static gg.modl.proto.modl.v1.UsageResponse toUsageResponse(UsageResponse model) {
        gg.modl.proto.modl.v1.UsageResponse.Builder builder = gg.modl.proto.modl.v1.UsageResponse.newBuilder()
            .setTotalOverageCost(model.totalOverageCost())
            .setUsageBillingEnabled(model.usageBillingEnabled());
        if (model.period() != null) {
            builder.setPeriod(toPeriod(model.period()));
        }
        if (model.ai() != null) {
            builder.setAi(toUsageMetric(model.ai()));
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.UsageBillingSettingsResponse toUsageBillingSettingsResponse(UsageBillingSettingsResponse model) {
        return gg.modl.proto.modl.v1.UsageBillingSettingsResponse.newBuilder()
            .setSuccess(model.success())
            .setMessage(stringValue(model.message()))
            .setUsageBillingEnabled(model.usageBillingEnabled())
            .build();
    }

    static gg.modl.proto.modl.v1.UpdateStorageLimitResponse toUpdateStorageLimitResponse(long maxStorageLimitBytes) {
        return gg.modl.proto.modl.v1.UpdateStorageLimitResponse.newBuilder()
            .setSuccess(true)
            .setMaxStorageLimitBytes(maxStorageLimitBytes)
            .build();
    }

    static gg.modl.proto.modl.v1.UpdateOverageLimitsResponse toUpdateOverageLimitsResponse(long maxStorageLimitBytes,
                                                                                          int maxAiOverageRequests) {
        return gg.modl.proto.modl.v1.UpdateOverageLimitsResponse.newBuilder()
            .setSuccess(true)
            .setMaxStorageLimitBytes(maxStorageLimitBytes)
            .setMaxAiOverageRequests(maxAiOverageRequests)
            .build();
    }

    private static gg.modl.proto.modl.v1.UsageResponse.Period toPeriod(UsageResponse.Period period) {
        gg.modl.proto.modl.v1.UsageResponse.Period.Builder builder = gg.modl.proto.modl.v1.UsageResponse.Period.newBuilder();
        if (period.start() != null) {
            builder.setStart(toTimestamp(period.start()));
        }
        if (period.end() != null) {
            builder.setEnd(toTimestamp(period.end()));
        }
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.UsageResponse.UsageMetric toUsageMetric(UsageResponse.UsageMetric metric) {
        return gg.modl.proto.modl.v1.UsageResponse.UsageMetric.newBuilder()
            .setUsed(metric.used())
            .setLimit(metric.limit())
            .setOverage(metric.overage())
            .setOverageRate(metric.overageRate())
            .setOverageCost(metric.overageCost())
            .setPercentage(metric.percentage())
            .build();
    }
}
