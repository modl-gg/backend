package gg.modl.backend.admin.dto.request;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public record UpdateSystemConfigRequest(
    @Valid GeneralConfigRequest general,
    @Valid LoggingConfigRequest logging,
    @Valid SecurityConfigRequest security,
    @Valid NotificationsConfigRequest notifications,
    @Valid PerformanceConfigRequest performance,
    @Valid FeaturesConfigRequest features
) {
    public void applyTo(SystemConfig target) {
        if (general != null) {
            general.applyOnto(target.getGeneral());
        }
        if (logging != null) {
            logging.applyOnto(target.getLogging());
        }
        if (security != null) {
            security.applyOnto(target.getSecurity());
        }
        if (notifications != null) {
            notifications.applyOnto(target.getNotifications());
        }
        if (performance != null) {
            performance.applyOnto(target.getPerformance());
        }
        if (features != null) {
            features.applyOnto(target.getFeatures());
        }
    }

    @AssertTrue(message = "At least one config section must be provided")
    public boolean hasConfigSection() {
        return general != null
               || logging != null
               || security != null
               || notifications != null
               || performance != null
               || features != null;
    }

    public record GeneralConfigRequest(
        @Size(max = RequestValidationLimits.ADMIN_SYSTEM_NAME_MAX_LENGTH)
        @NotBlank
        String systemName,

        @Email
        @Size(max = RequestValidationLimits.EMAIL_MAX_LENGTH)
        String adminEmail,

        @Size(max = RequestValidationLimits.TIMEZONE_MAX_LENGTH)
        String timezone,

        @Size(max = RequestValidationLimits.ADMIN_DEFAULT_LANGUAGE_MAX_LENGTH)
        String defaultLanguage,

        Boolean maintenanceMode,

        @Size(max = RequestValidationLimits.MAINTENANCE_MESSAGE_MAX_LENGTH)
        String maintenanceMessage
    ) {
        public void applyOnto(SystemConfig.GeneralConfig config) {
            if (systemName != null) {
                config.setSystemName(systemName);
            }
            if (adminEmail != null) {
                config.setAdminEmail(adminEmail);
            }
            if (timezone != null) {
                config.setTimezone(timezone);
            }
            if (defaultLanguage != null) {
                config.setDefaultLanguage(defaultLanguage);
            }
            if (maintenanceMode != null) {
                config.setMaintenanceMode(maintenanceMode);
            }
            if (maintenanceMessage != null) {
                config.setMaintenanceMessage(maintenanceMessage);
            }
        }
    }

    public record LoggingConfigRequest(
        Boolean pm2LoggingEnabled,
        @Min(RequestValidationLimits.LOG_RETENTION_DAYS_MIN)
        @Max(RequestValidationLimits.LOG_RETENTION_DAYS_MAX)
        Integer logRetentionDays,
        @Min(RequestValidationLimits.MAX_LOG_SIZE_PER_DAY_MIN)
        @Max(RequestValidationLimits.MAX_LOG_SIZE_PER_DAY_MAX)
        Integer maxLogSizePerDay
    ) {
        public void applyOnto(SystemConfig.LoggingConfig config) {
            if (pm2LoggingEnabled != null) {
                config.setPm2LoggingEnabled(pm2LoggingEnabled);
            }
            if (logRetentionDays != null) {
                config.setLogRetentionDays(logRetentionDays);
            }
            if (maxLogSizePerDay != null) {
                config.setMaxLogSizePerDay(maxLogSizePerDay);
            }
        }
    }

    public record SecurityConfigRequest(
        @Min(RequestValidationLimits.SESSION_TIMEOUT_MINUTES_MIN)
        @Max(RequestValidationLimits.SESSION_TIMEOUT_MINUTES_MAX)
        Integer sessionTimeout,
        @Min(RequestValidationLimits.MAX_LOGIN_ATTEMPTS_MIN)
        @Max(RequestValidationLimits.MAX_LOGIN_ATTEMPTS_MAX)
        Integer maxLoginAttempts,
        @Min(RequestValidationLimits.LOCKOUT_DURATION_MINUTES_MIN)
        @Max(RequestValidationLimits.LOCKOUT_DURATION_MINUTES_MAX)
        Integer lockoutDuration,
        Boolean requireTwoFactor,
        @Min(RequestValidationLimits.PASSWORD_MIN_LENGTH_MIN)
        @Max(RequestValidationLimits.PASSWORD_MIN_LENGTH_MAX)
        Integer passwordMinLength,
        Boolean passwordRequireSpecial,
        @Size(max = RequestValidationLimits.IP_WHITELIST_MAX_ENTRIES)
        List<
            @Size(max = RequestValidationLimits.IP_WHITELIST_ENTRY_MAX_LENGTH)
                String
            > ipWhitelist,
        @Size(max = RequestValidationLimits.CORS_ORIGIN_MAX_ENTRIES)
        List<
            @Size(max = RequestValidationLimits.CORS_ORIGIN_MAX_LENGTH)
                String
            > corsOrigins
    ) {
        public void applyOnto(SystemConfig.SecurityConfig config) {
            if (sessionTimeout != null) {
                config.setSessionTimeout(sessionTimeout);
            }
            if (maxLoginAttempts != null) {
                config.setMaxLoginAttempts(maxLoginAttempts);
            }
            if (lockoutDuration != null) {
                config.setLockoutDuration(lockoutDuration);
            }
            if (requireTwoFactor != null) {
                config.setRequireTwoFactor(requireTwoFactor);
            }
            if (passwordMinLength != null) {
                config.setPasswordMinLength(passwordMinLength);
            }
            if (passwordRequireSpecial != null) {
                config.setPasswordRequireSpecial(passwordRequireSpecial);
            }
            if (ipWhitelist != null) {
                config.setIpWhitelist(new ArrayList<>(ipWhitelist));
            }
            if (corsOrigins != null) {
                config.setCorsOrigins(new ArrayList<>(corsOrigins));
            }
        }
    }

    public record NotificationsConfigRequest(
        Boolean emailNotifications,
        Boolean criticalAlerts,
        Boolean weeklyReports,
        Boolean maintenanceAlerts,
        @Size(max = RequestValidationLimits.WEBHOOK_URL_MAX_LENGTH)
        String slackWebhook,
        @Size(max = RequestValidationLimits.WEBHOOK_URL_MAX_LENGTH)
        String discordWebhook
    ) {
        public void applyOnto(SystemConfig.NotificationsConfig config) {
            if (emailNotifications != null) {
                config.setEmailNotifications(emailNotifications);
            }
            if (criticalAlerts != null) {
                config.setCriticalAlerts(criticalAlerts);
            }
            if (weeklyReports != null) {
                config.setWeeklyReports(weeklyReports);
            }
            if (maintenanceAlerts != null) {
                config.setMaintenanceAlerts(maintenanceAlerts);
            }
            if (slackWebhook != null) {
                config.setSlackWebhook(slackWebhook);
            }
            if (discordWebhook != null) {
                config.setDiscordWebhook(discordWebhook);
            }
        }
    }

    public record PerformanceConfigRequest(
        @Min(RequestValidationLimits.CACHE_TTL_SECONDS_MIN)
        @Max(RequestValidationLimits.CACHE_TTL_SECONDS_MAX)
        Integer cacheTtl,
        @Min(RequestValidationLimits.RATE_LIMIT_REQUESTS_MIN)
        @Max(RequestValidationLimits.RATE_LIMIT_REQUESTS_MAX)
        Integer rateLimitRequests,
        @Min(RequestValidationLimits.RATE_LIMIT_WINDOW_SECONDS_MIN)
        @Max(RequestValidationLimits.RATE_LIMIT_WINDOW_SECONDS_MAX)
        Integer rateLimitWindow,
        @Min(RequestValidationLimits.DATABASE_CONNECTION_POOL_MIN)
        @Max(RequestValidationLimits.DATABASE_CONNECTION_POOL_MAX)
        Integer databaseConnectionPool,
        Boolean enableCompression,
        Boolean enableCaching
    ) {
        public void applyOnto(SystemConfig.PerformanceConfig config) {
            if (cacheTtl != null) {
                config.setCacheTtl(cacheTtl);
            }
            if (rateLimitRequests != null) {
                config.setRateLimitRequests(rateLimitRequests);
            }
            if (rateLimitWindow != null) {
                config.setRateLimitWindow(rateLimitWindow);
            }
            if (databaseConnectionPool != null) {
                config.setDatabaseConnectionPool(databaseConnectionPool);
            }
            if (enableCompression != null) {
                config.setEnableCompression(enableCompression);
            }
            if (enableCaching != null) {
                config.setEnableCaching(enableCaching);
            }
        }
    }

    public record FeaturesConfigRequest(
        Boolean analyticsEnabled,
        Boolean auditLoggingEnabled,
        Boolean apiAccessEnabled,
        Boolean bulkOperationsEnabled,
        Boolean advancedFiltering,
        Boolean realTimeUpdates
    ) {
        public void applyOnto(SystemConfig.FeaturesConfig config) {
            if (analyticsEnabled != null) {
                config.setAnalyticsEnabled(analyticsEnabled);
            }
            if (auditLoggingEnabled != null) {
                config.setAuditLoggingEnabled(auditLoggingEnabled);
            }
            if (apiAccessEnabled != null) {
                config.setApiAccessEnabled(apiAccessEnabled);
            }
            if (bulkOperationsEnabled != null) {
                config.setBulkOperationsEnabled(bulkOperationsEnabled);
            }
            if (advancedFiltering != null) {
                config.setAdvancedFiltering(advancedFiltering);
            }
            if (realTimeUpdates != null) {
                config.setRealTimeUpdates(realTimeUpdates);
            }
        }
    }
}
