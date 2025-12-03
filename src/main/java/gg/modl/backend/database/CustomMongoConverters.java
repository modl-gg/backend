package gg.modl.backend.database;

import gg.modl.backend.server.data.CustomDomainStatus;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CustomMongoConverters {
    final static List<Converter<?, ?>> CONVERTERS = Arrays.asList(
            // ProvisioningStatus converters
            new StringToProvisioningStatusConverter(),
            new ProvisioningStatusToStringConverter(),
            // SubscriptionStatus converters
            new StringToSubscriptionStatusConverter(),
            new SubscriptionStatusToStringConverter(),
            // ServerPlan converters
            new StringToServerPlanConverter(),
            new ServerPlanToStringConverter(),
            // CustomDomainStatus converters
            new StringToCustomDomainStatusConverter(),
            new CustomDomainStatusToStringConverter()
    );

    // ProvisioningStatus converters
    private static class StringToProvisioningStatusConverter implements Converter<String, ProvisioningStatus> {
        @Override
        public ProvisioningStatus convert(@NotNull String source) {
            return switch (source.toLowerCase()) {
                case "pending" -> ProvisioningStatus.PENDING;
                case "in_progress" -> ProvisioningStatus.IN_PROGRESS;
                case "completed" -> ProvisioningStatus.COMPLETED;
                case "failed" -> ProvisioningStatus.FAILED;
                default -> null;
            };
        }
    }

    private static class ProvisioningStatusToStringConverter implements Converter<ProvisioningStatus, String> {
        @Override
        public String convert(@NotNull ProvisioningStatus source) {
            return switch (source) {
                case PENDING -> "pending";
                case IN_PROGRESS -> "in_progress";
                case COMPLETED -> "completed";
                case FAILED -> "failed";
            };
        }
    }

    // SubscriptionStatus converters
    private static class StringToSubscriptionStatusConverter implements Converter<String, SubscriptionStatus> {
        @Override
        public SubscriptionStatus convert(@NotNull String source) {
            return switch (source.toLowerCase()) {
                case "active" -> SubscriptionStatus.ACTIVE;
                case "canceled" -> SubscriptionStatus.CANCELED;
                case "past_due" -> SubscriptionStatus.PAST_DUE;
                case "inactive" -> SubscriptionStatus.INACTIVE;
                case "trialing" -> SubscriptionStatus.TRIALING;
                case "incomplete" -> SubscriptionStatus.INCOMPLETE;
                case "incomplete_expired" -> SubscriptionStatus.INCOMPLETE_EXPIRED;
                case "unpaid" -> SubscriptionStatus.UNPAID;
                case "paused" -> SubscriptionStatus.PAUSED;
                default -> null;
            };
        }
    }

    private static class SubscriptionStatusToStringConverter implements Converter<SubscriptionStatus, String> {
        @Override
        public String convert(@NotNull SubscriptionStatus source) {
            return switch (source) {
                case ACTIVE -> "active";
                case CANCELED -> "canceled";
                case PAST_DUE -> "past_due";
                case INACTIVE -> "inactive";
                case TRIALING -> "trialing";
                case INCOMPLETE -> "incomplete";
                case INCOMPLETE_EXPIRED -> "incomplete_expired";
                case UNPAID -> "unpaid";
                case PAUSED -> "paused";
            };
        }
    }

    // ServerPlan converters
    private static class StringToServerPlanConverter implements Converter<String, ServerPlan> {
        @Override
        public ServerPlan convert(@NotNull String source) {
            return switch (source.toLowerCase()) {
                case "free" -> ServerPlan.FREE;
                case "premium" -> ServerPlan.PREMIUM;
                default -> null;
            };
        }
    }

    private static class ServerPlanToStringConverter implements Converter<ServerPlan, String> {
        @Override
        public String convert(@NotNull ServerPlan source) {
            return switch (source) {
                case FREE -> "free";
                case PREMIUM -> "premium";
            };
        }
    }

    // CustomDomainStatus converters
    private static class StringToCustomDomainStatusConverter implements Converter<String, CustomDomainStatus> {
        @Override
        public CustomDomainStatus convert(@NotNull String source) {
            return switch (source.toLowerCase()) {
                case "pending" -> CustomDomainStatus.PENDING;
                case "error" -> CustomDomainStatus.ERROR;
                case "active" -> CustomDomainStatus.ACTIVE;
                case "verifying" -> CustomDomainStatus.VERIFYING;
                default -> null;
            };
        }
    }

    private static class CustomDomainStatusToStringConverter implements Converter<CustomDomainStatus, String> {
        @Override
        public String convert(@NotNull CustomDomainStatus source) {
            return switch (source) {
                case PENDING -> "pending";
                case ERROR -> "error";
                case ACTIVE -> "active";
                case VERIFYING -> "verifying";
            };
        }
    }
}

