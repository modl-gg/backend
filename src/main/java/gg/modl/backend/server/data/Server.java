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
    @Indexed(name = "uidx_servers_serverName", unique = true)
    private final String serverName;

    @NotNull
    @Field(name = ServerField.SUBDOMAIN, targetType = FieldType.STRING)
    @Indexed(name = "uidx_servers_customDomain", unique = true)
    private final String customDomain;

    @Nullable
    @Field(name = "databaseName", targetType = FieldType.STRING)
    private final String databaseName;

    // Admin & Verification
    @NotNull
    @Field(name = ServerField.ADMIN_EMAIL, targetType = FieldType.STRING)
    @Indexed(name = "uidx_servers_adminEmail", unique = true)
    private String adminEmail;

    @NotNull
    @Field(name = "emailVerified", targetType = FieldType.BOOLEAN)
    @Indexed(name = "idx_servers_emailVerified")
    private Boolean emailVerified;

    @Nullable
    @Field(name = "emailVerificationToken", targetType = FieldType.STRING)
    @Indexed(name = "uidx_servers_emailVerificationToken", unique = true, sparse = true)
    private String emailVerificationToken;

    // Provisioning & Status
    @Nullable
    @Field(name = "provisioningStatus", targetType = FieldType.STRING)
    @Indexed(name = "idx_servers_provisioningStatus")
    private ProvisioningStatus provisioningStatus;

    @Nullable
    @Field(name = "provisioningNotes", targetType = FieldType.STRING)
    private String provisioningNotes;

    @Nullable
    @Field(name = "provisioningSignInToken", targetType = FieldType.STRING)
    @Indexed(name = "uidx_servers_provisioningSignInToken", unique = true, sparse = true)
    private String provisioningSignInToken;

    @Nullable
    @Field(name = "provisioningSignInTokenExpiresAt", targetType = FieldType.DATE_TIME)
    private Date provisioningSignInTokenExpiresAt;

    // Plan & Billing
    @NotNull
    @Field(name = "plan", targetType = FieldType.STRING)
    private ServerPlan plan;

    @Nullable
    @Field(name = "subscriptionStatus", targetType = FieldType.STRING)
    private SubscriptionStatus subscriptionStatus;

    @Nullable
    @Field(name = "currentPeriodStart", targetType = FieldType.DATE_TIME)
    private Date currentPeriodStart;

    @Nullable
    @Field(name = "currentPeriodEnd", targetType = FieldType.DATE_TIME)
    private Date currentPeriodEnd;

    @Nullable
    @Field(name = "stripeCustomerId", targetType = FieldType.STRING)
    @Indexed(name = "uidx_servers_stripeCustomerId", unique = true, sparse = true)
    private String stripeCustomerId;

    @Nullable
    @Field(name = "stripeSubscriptionId", targetType = FieldType.STRING)
    @Indexed(name = "uidx_servers_stripeSubscriptionId", unique = true, sparse = true)
    private String stripeSubscriptionId;

    // Usage Tracking & Billing
    @Nullable
    @Field(name = "cdnUsageCurrentPeriod", targetType = FieldType.DOUBLE)
    private Double cdnUsageCurrentPeriod; // GB used in current billing period

    @Nullable
    @Field(name = "aiRequestsCurrentPeriod", targetType = FieldType.INT64)
    private Long aiRequestsCurrentPeriod; // AI requests used in current billing period

    @Nullable
    @Field(name = "usageBillingEnabled", targetType = FieldType.BOOLEAN)
    private Boolean usageBillingEnabled; // Whether to charge for overages

    @Nullable
    @Field(name = "usageBillingUpdatedAt", targetType = FieldType.DATE_TIME)
    private Date usageBillingUpdatedAt;

    @Nullable
    @Field(name = "maxStorageLimitBytes", targetType = FieldType.INT64)
    private Long maxStorageLimitBytes;

    @Nullable
    @Field(name = "maxAiOverageRequests", targetType = FieldType.INT64)
    private Long maxAiOverageRequests;

    // Migration Settings
    @Nullable
    @Field(name = "migrationFileSizeLimit", targetType = FieldType.INT64)
    private Long migrationFileSizeLimit; // Custom migration file size limit in bytes

    // Custom Domain Management
    @Nullable
    @Field(name = ServerField.CUSTOM_DOMAIN, targetType = FieldType.STRING)
    @Indexed(name = "uidx_servers_customDomainOverride", unique = true, sparse = true)
    private String customDomainOverride;

    @Nullable
    @Field(name = ServerField.CUSTOM_DOMAIN_STATUS, targetType = FieldType.STRING)
    private CustomDomainStatus customDomainStatus;

    @Nullable
    @Field(name = "customDomainLastChecked", targetType = FieldType.DATE_TIME)
    private Date customDomainLastChecked;

    @Nullable
    @Field(name = "customDomainError", targetType = FieldType.STRING)
    private String customDomainError;

    @Nullable
    @Field(name = "customDomainCloudflareId", targetType = FieldType.STRING)
    @Indexed(name = "uidx_servers_customDomainCloudflareId", unique = true, sparse = true)
    private String customDomainCloudflareId;

    @Nullable
    @Field(name = ServerField.CUSTOM_DOMAIN_GRANDFATHERED, targetType = FieldType.BOOLEAN)
    private Boolean customDomainGrandfathered;

    // API Key
    @Nullable
    @Field(name = "apiKey", targetType = FieldType.STRING)
    @Indexed(name = "uidx_servers_apiKey", unique = true, sparse = true)
    private String apiKey;

    // Analytics/Stats
    @Nullable
    @Field(name = "onlinePlayerCount", targetType = FieldType.INT64)
    private Long onlinePlayerCount;

    @Nullable
    @Field(name = "userCount", targetType = FieldType.INT64)
    @Indexed(name = "idx_servers_userCount")
    private Long userCount;

    @Nullable
    @Field(name = "ticketCount", targetType = FieldType.INT64)
    @Indexed(name = "idx_servers_ticketCount")
    private Long ticketCount;

    @Nullable
    @Field(name = "lastStatsUpdatedAt", targetType = FieldType.DATE_TIME)
    @Indexed(name = "idx_servers_lastStatsUpdatedAt")
    private Date lastStatsUpdatedAt;

    @Nullable
    @Field(name = "lastActivityAt", targetType = FieldType.DATE_TIME)
    private Date lastActivityAt;

    // Timestamps
    @Nullable
    @Field(name = "createdAt", targetType = FieldType.DATE_TIME)
    @Indexed(name = "idx_servers_createdAt")
    private Date createdAt;

    @Nullable
    @Field(name = "updatedAt", targetType = FieldType.DATE_TIME)
    private Date updatedAt;

    @Nullable
    @Field(name = "staffPermissionsUpdatedAt", targetType = FieldType.DATE_TIME)
    private Date staffPermissionsUpdatedAt;

    @Nullable
    @Field(name = "punishmentTypesUpdatedAt", targetType = FieldType.DATE_TIME)
    private Date punishmentTypesUpdatedAt;
}
