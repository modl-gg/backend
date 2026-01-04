package gg.modl.backend.server.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.server.ServerField;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.Date;

@Document(collection = CollectionName.MODL_SERVERS)
@Data
@RequiredArgsConstructor
public class Server {
    // Core Identifiers
    @Id
    @Field(targetType = FieldType.OBJECT_ID)
    private String id;

    @NotNull
    @Field(name = ServerField.SERVER_NAME, targetType = FieldType.STRING)
    @Indexed(unique = true)
    private final String serverName;

    @NotNull
    @Field(name = ServerField.SUBDOMAIN, targetType = FieldType.STRING)
    @Indexed(unique = true)
    private final String customDomain;

    @Nullable
    @Field(name = "databaseName", targetType = FieldType.STRING)
    private final String databaseName;

    // Admin & Verification
    @NotNull
    @Field(name = ServerField.ADMIN_EMAIL, targetType = FieldType.STRING)
    @Indexed(unique = true)
    private String adminEmail;

    @NotNull
    @Field(name = "emailVerified", targetType = FieldType.BOOLEAN)
    @Indexed
    private Boolean emailVerified;

    @Nullable
    @Field(name = "emailVerificationToken", targetType = FieldType.STRING)
    @Indexed(unique = true, sparse = true)
    private String emailVerificationToken;

    // Provisioning & Status
    @NotNull
    @Field(name = "provisioningStatus", targetType = FieldType.STRING)
    @Indexed
    private ProvisioningStatus provisioningStatus;

    @Nullable
    @Field(name = "provisioningNotes", targetType = FieldType.STRING)
    private String provisioningNotes;

    @Nullable
    @Field(name = "provisioningSignInToken", targetType = FieldType.STRING)
    @Indexed(unique = true, sparse = true)
    private String provisioningSignInToken;

    @Nullable
    @Field(name = "provisioningSignInTokenExpiresAt", targetType = FieldType.DATE_TIME)
    private Date provisioningSignInTokenExpiresAt;

    // Plan & Billing
    @NotNull
    @Field(name = "plan", targetType = FieldType.STRING)
    private ServerPlan plan;

    @NotNull
    @Field(name = "subscription_status", targetType = FieldType.STRING)
    private SubscriptionStatus subscriptionStatus;

    @Nullable
    @Field(name = "current_period_start", targetType = FieldType.DATE_TIME)
    private Date currentPeriodStart;

    @Nullable
    @Field(name = "current_period_end", targetType = FieldType.DATE_TIME)
    private Date currentPeriodEnd;

    @Nullable
    @Field(name = "stripe_customer_id", targetType = FieldType.STRING)
    @Indexed(unique = true, sparse = true)
    private String stripeCustomerId;

    @Nullable
    @Field(name = "stripe_subscription_id", targetType = FieldType.STRING)
    @Indexed(unique = true, sparse = true)
    private String stripeSubscriptionId;

    // Usage Tracking & Billing
    @Nullable
    @Field(name = "cdn_usage_current_period", targetType = FieldType.DOUBLE)
    private Double cdnUsageCurrentPeriod; // GB used in current billing period

    @Nullable
    @Field(name = "ai_requests_current_period", targetType = FieldType.INT64)
    private Long aiRequestsCurrentPeriod; // AI requests used in current billing period

    @Nullable
    @Field(name = "usage_billing_enabled", targetType = FieldType.BOOLEAN)
    private Boolean usageBillingEnabled; // Whether to charge for overages

    @Nullable
    @Field(name = "usage_billing_updated_at", targetType = FieldType.DATE_TIME)
    private Date usageBillingUpdatedAt;

    // Migration Settings
    @Nullable
    @Field(name = "migrationFileSizeLimit", targetType = FieldType.INT64)
    private Long migrationFileSizeLimit; // Custom migration file size limit in bytes

    // Custom Domain Management
    @Nullable
    @Field(name = ServerField.CUSTOM_DOMAIN, targetType = FieldType.STRING)
    @Indexed(unique = true, sparse = true)
    private String customDomainOverride;

    @Nullable
    @Field(name = ServerField.CUSTOM_DOMAIN_STATUS, targetType = FieldType.STRING)
    private CustomDomainStatus customDomainStatus;

    @Nullable
    @Field(name = "customDomain_lastChecked", targetType = FieldType.DATE_TIME)
    private Date customDomainLastChecked;

    @Nullable
    @Field(name = "customDomain_error", targetType = FieldType.STRING)
    private String customDomainError;

    @Nullable
    @Field(name = "customDomain_cloudflareId", targetType = FieldType.STRING)
    @Indexed(unique = true, sparse = true)
    private String customDomainCloudflareId;

    // API Key
    @Nullable
    @Field(name = "apiKey", targetType = FieldType.STRING)
    @Indexed(unique = true, sparse = true)
    private String apiKey;

    // Analytics/Stats
    @Nullable
    @Field(name = "lastActivityAt", targetType = FieldType.DATE_TIME)
    private Date lastActivityAt;

    // Timestamps
    @NotNull
    @Field(name = "createdAt", targetType = FieldType.DATE_TIME)
    @Indexed
    private Date createdAt;

    @NotNull
    @Field(name = "updatedAt", targetType = FieldType.DATE_TIME)
    private Date updatedAt;
}
