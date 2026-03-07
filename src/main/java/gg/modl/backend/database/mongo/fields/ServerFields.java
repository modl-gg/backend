package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;

public final class ServerFields {
    public static final MongoField<Server> ID = MongoFieldNames.field(Server.class, Server::getId);
    public static final MongoField<Server> SERVER_NAME = MongoFieldNames.field(Server.class, Server::getServerName);
    public static final MongoField<Server> CUSTOM_DOMAIN = MongoFieldNames.field(Server.class, Server::getCustomDomain);
    public static final MongoField<Server> DATABASE_NAME = MongoFieldNames.field(Server.class, Server::getDatabaseName);
    public static final MongoField<Server> ADMIN_EMAIL = MongoFieldNames.field(Server.class, Server::getAdminEmail);
    public static final MongoField<Server> EMAIL_VERIFIED = MongoFieldNames.field(Server.class, Server::getEmailVerified);
    public static final MongoField<Server> EMAIL_VERIFICATION_TOKEN = MongoFieldNames.field(Server.class, Server::getEmailVerificationToken);
    public static final MongoField<Server> PROVISIONING_STATUS = MongoFieldNames.field(Server.class, Server::getProvisioningStatus);
    public static final MongoField<Server> PROVISIONING_NOTES = MongoFieldNames.field(Server.class, Server::getProvisioningNotes);
    public static final MongoField<Server> PROVISIONING_SIGN_IN_TOKEN = MongoFieldNames.field(Server.class, Server::getProvisioningSignInToken);
    public static final MongoField<Server> PROVISIONING_SIGN_IN_TOKEN_EXPIRES_AT = MongoFieldNames.field(Server.class, Server::getProvisioningSignInTokenExpiresAt);
    public static final MongoField<Server> PLAN = MongoFieldNames.field(Server.class, Server::getPlan);
    public static final MongoField<Server> SUBSCRIPTION_STATUS = MongoFieldNames.field(Server.class, Server::getSubscriptionStatus);
    public static final MongoField<Server> CURRENT_PERIOD_START = MongoFieldNames.field(Server.class, Server::getCurrentPeriodStart);
    public static final MongoField<Server> CURRENT_PERIOD_END = MongoFieldNames.field(Server.class, Server::getCurrentPeriodEnd);
    public static final MongoField<Server> STRIPE_CUSTOMER_ID = MongoFieldNames.field(Server.class, Server::getStripeCustomerId);
    public static final MongoField<Server> STRIPE_SUBSCRIPTION_ID = MongoFieldNames.field(Server.class, Server::getStripeSubscriptionId);
    public static final MongoField<Server> CDN_USAGE_CURRENT_PERIOD = MongoFieldNames.field(Server.class, Server::getCdnUsageCurrentPeriod);
    public static final MongoField<Server> AI_REQUESTS_CURRENT_PERIOD = MongoFieldNames.field(Server.class, Server::getAiRequestsCurrentPeriod);
    public static final MongoField<Server> USAGE_BILLING_ENABLED = MongoFieldNames.field(Server.class, Server::getUsageBillingEnabled);
    public static final MongoField<Server> USAGE_BILLING_UPDATED_AT = MongoFieldNames.field(Server.class, Server::getUsageBillingUpdatedAt);
    public static final MongoField<Server> MAX_STORAGE_LIMIT_BYTES = MongoFieldNames.field(Server.class, Server::getMaxStorageLimitBytes);
    public static final MongoField<Server> MAX_AI_OVERAGE_REQUESTS = MongoFieldNames.field(Server.class, Server::getMaxAiOverageRequests);
    public static final MongoField<Server> MIGRATION_FILE_SIZE_LIMIT = MongoFieldNames.field(Server.class, Server::getMigrationFileSizeLimit);
    public static final MongoField<Server> ONLINE_PLAYER_COUNT = MongoFieldNames.field(Server.class, Server::getOnlinePlayerCount);
    public static final MongoField<Server> USER_COUNT = MongoFieldNames.field(Server.class, Server::getUserCount);
    public static final MongoField<Server> TICKET_COUNT = MongoFieldNames.field(Server.class, Server::getTicketCount);
    public static final MongoField<Server> LAST_STATS_UPDATED_AT = MongoFieldNames.field(Server.class, Server::getLastStatsUpdatedAt);
    public static final MongoField<Server> LAST_ACTIVITY_AT = MongoFieldNames.field(Server.class, Server::getLastActivityAt);
    public static final MongoField<Server> CUSTOM_DOMAIN_OVERRIDE = MongoFieldNames.field(Server.class, Server::getCustomDomainOverride);
    public static final MongoField<Server> CUSTOM_DOMAIN_STATUS = MongoFieldNames.field(Server.class, Server::getCustomDomainStatus);
    public static final MongoField<Server> CUSTOM_DOMAIN_CLOUDFLARE_ID = MongoFieldNames.field(Server.class, Server::getCustomDomainCloudflareId);
    public static final MongoField<Server> CUSTOM_DOMAIN_ERROR = MongoFieldNames.field(Server.class, Server::getCustomDomainError);
    public static final MongoField<Server> CUSTOM_DOMAIN_LAST_CHECKED = MongoFieldNames.field(Server.class, Server::getCustomDomainLastChecked);
    public static final MongoField<Server> CUSTOM_DOMAIN_GRANDFATHERED = MongoFieldNames.field(Server.class, Server::getCustomDomainGrandfathered);
    public static final MongoField<Server> API_KEY = MongoFieldNames.field(Server.class, Server::getApiKey);
    public static final MongoField<Server> CREATED_AT = MongoFieldNames.field(Server.class, Server::getCreatedAt);
    public static final MongoField<Server> UPDATED_AT = MongoFieldNames.field(Server.class, Server::getUpdatedAt);
    public static final MongoField<Server> STAFF_PERMISSIONS_UPDATED_AT = MongoFieldNames.field(Server.class, Server::getStaffPermissionsUpdatedAt);
    public static final MongoField<Server> PUNISHMENT_TYPES_UPDATED_AT = MongoFieldNames.field(Server.class, Server::getPunishmentTypesUpdatedAt);

    private ServerFields() {
    }
}
