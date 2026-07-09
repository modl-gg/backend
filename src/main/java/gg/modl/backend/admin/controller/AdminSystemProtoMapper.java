package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.admin.dto.request.ToggleMaintenanceRequest;
import gg.modl.backend.admin.dto.request.UpdatePromptRequest;
import gg.modl.backend.admin.dto.request.UpdateRateLimitsRequest;
import gg.modl.backend.admin.dto.request.UpdateSystemConfigRequest;
import gg.modl.proto.modl.v1.AdminSystemConfig;
import gg.modl.proto.modl.v1.AdminSystemConfigResponse;
import gg.modl.proto.modl.v1.AdminSystemFeaturesConfig;
import gg.modl.proto.modl.v1.AdminSystemGeneralConfig;
import gg.modl.proto.modl.v1.AdminSystemLoggingConfig;
import gg.modl.proto.modl.v1.AdminSystemMaintenanceResponse;
import gg.modl.proto.modl.v1.AdminSystemMaintenanceStatus;
import gg.modl.proto.modl.v1.AdminSystemNotificationsConfig;
import gg.modl.proto.modl.v1.AdminSystemPerformanceConfig;
import gg.modl.proto.modl.v1.AdminSystemPrompt;
import gg.modl.proto.modl.v1.AdminSystemPromptResponse;
import gg.modl.proto.modl.v1.AdminSystemRateLimitsData;
import gg.modl.proto.modl.v1.AdminSystemRateLimitsResponse;
import gg.modl.proto.modl.v1.AdminSystemRateLimitsUpdateResponse;
import gg.modl.proto.modl.v1.AdminSystemSecurityConfig;
import gg.modl.proto.modl.v1.AdminSystemServiceRestartData;
import gg.modl.proto.modl.v1.AdminSystemServiceRestartResponse;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.booleanValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

final class AdminSystemProtoMapper {

    private AdminSystemProtoMapper() {
    }

    static UpdateSystemConfigRequest fromUpdateConfig(gg.modl.proto.modl.v1.UpdateSystemConfigRequest request) {
        return new UpdateSystemConfigRequest(
            request.hasGeneral() ? fromGeneral(request.getGeneral()) : null,
            request.hasLogging() ? fromLogging(request.getLogging()) : null,
            request.hasSecurity() ? fromSecurity(request.getSecurity()) : null,
            request.hasNotifications() ? fromNotifications(request.getNotifications()) : null,
            request.hasPerformance() ? fromPerformance(request.getPerformance()) : null,
            request.hasFeatures() ? fromFeatures(request.getFeatures()) : null
        );
    }

    static ToggleMaintenanceRequest fromToggleMaintenance(gg.modl.proto.modl.v1.ToggleMaintenanceRequest request) {
        boolean enabled = request.hasEnabledValue() ? request.getEnabledValue() : request.getEnabled();
        String message = request.hasMessage() ? request.getMessage() : null;
        return new ToggleMaintenanceRequest(enabled, message);
    }

    static UpdateRateLimitsRequest fromUpdateRateLimits(gg.modl.proto.modl.v1.UpdateRateLimitsRequest request) {
        return new UpdateRateLimitsRequest(
            request.hasRateLimitRequests() ? request.getRateLimitRequests() : null,
            request.hasRateLimitWindow() ? request.getRateLimitWindow() : null
        );
    }

    static UpdatePromptRequest fromUpdatePrompt(gg.modl.proto.modl.v1.UpdatePromptRequest request) {
        return new UpdatePromptRequest(request.getPrompt());
    }

    static AdminSystemConfigResponse toConfigResponse(SystemConfig config, String message) {
        AdminSystemConfigResponse.Builder builder = AdminSystemConfigResponse.newBuilder()
            .setSuccess(true)
            .setData(toConfig(config));
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    static AdminSystemMaintenanceResponse toMaintenanceResponse(Map<String, Object> status, String message) {
        AdminSystemMaintenanceStatus.Builder data = AdminSystemMaintenanceStatus.newBuilder()
            .setIsActive(booleanValue(status.get("isActive")));
        Object messageValue = status.get("message");
        if (messageValue != null) {
            data.setMessage(stringValue(messageValue));
        }
        AdminSystemMaintenanceResponse.Builder builder = AdminSystemMaintenanceResponse.newBuilder()
            .setSuccess(true)
            .setData(data.build());
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    static AdminSystemRateLimitsResponse toRateLimitsResponse(Map<String, Object> status) {
        AdminSystemRateLimitsData.Builder data = AdminSystemRateLimitsData.newBuilder()
            .setActive(booleanValue(status.get("active")));
        Object current = status.get("current");
        if (current instanceof SystemConfig.PerformanceConfig performance) {
            data.setCurrent(toPerformance(performance));
        }
        Object resetTime = status.get("resetTime");
        if (resetTime != null) {
            data.setResetTime(toTimestamp(resetTime));
        }
        return AdminSystemRateLimitsResponse.newBuilder()
            .setSuccess(true)
            .setData(data.build())
            .build();
    }

    static AdminSystemRateLimitsUpdateResponse toRateLimitsUpdateResponse(SystemConfig.PerformanceConfig performance,
                                                                          String message) {
        AdminSystemRateLimitsUpdateResponse.Builder builder = AdminSystemRateLimitsUpdateResponse.newBuilder()
            .setSuccess(true)
            .setData(toPerformance(performance));
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    static AdminSystemPromptResponse toPromptResponse(SystemPrompt prompt, String message) {
        AdminSystemPromptResponse.Builder builder = AdminSystemPromptResponse.newBuilder()
            .setSuccess(true);
        if (prompt != null) {
            builder.setData(toPrompt(prompt));
        }
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    static AdminSystemServiceRestartResponse toServiceRestartResponse(String service, String status, Date requestedAt,
                                                                      String message) {
        AdminSystemServiceRestartData data = AdminSystemServiceRestartData.newBuilder()
            .setService(stringValue(service))
            .setStatus(stringValue(status))
            .setRequestedAt(toTimestamp(requestedAt))
            .build();
        AdminSystemServiceRestartResponse.Builder builder = AdminSystemServiceRestartResponse.newBuilder()
            .setSuccess(true)
            .setData(data);
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    private static AdminSystemConfig toConfig(SystemConfig config) {
        return AdminSystemConfig.newBuilder()
            .setId(stringValue(config.getId()))
            .setConfigId(stringValue(config.getConfigId()))
            .setGeneral(toGeneral(config.getGeneral()))
            .setLogging(toLogging(config.getLogging()))
            .setSecurity(toSecurity(config.getSecurity()))
            .setNotifications(toNotifications(config.getNotifications()))
            .setPerformance(toPerformance(config.getPerformance()))
            .setFeatures(toFeatures(config.getFeatures()))
            .setCreatedAt(toTimestamp(config.getCreatedAt()))
            .setUpdatedAt(toTimestamp(config.getUpdatedAt()))
            .build();
    }

    private static AdminSystemGeneralConfig toGeneral(SystemConfig.GeneralConfig general) {
        return AdminSystemGeneralConfig.newBuilder()
            .setSystemName(stringValue(general.getSystemName()))
            .setAdminEmail(stringValue(general.getAdminEmail()))
            .setTimezone(stringValue(general.getTimezone()))
            .setDefaultLanguage(stringValue(general.getDefaultLanguage()))
            .setMaintenanceMode(general.isMaintenanceMode())
            .setMaintenanceMessage(stringValue(general.getMaintenanceMessage()))
            .build();
    }

    private static AdminSystemLoggingConfig toLogging(SystemConfig.LoggingConfig logging) {
        return AdminSystemLoggingConfig.newBuilder()
            .setPm2LoggingEnabled(logging.isPm2LoggingEnabled())
            .setLogRetentionDays(logging.getLogRetentionDays())
            .setMaxLogSizePerDay(logging.getMaxLogSizePerDay())
            .build();
    }

    private static AdminSystemSecurityConfig toSecurity(SystemConfig.SecurityConfig security) {
        return AdminSystemSecurityConfig.newBuilder()
            .setSessionTimeout(security.getSessionTimeout())
            .setMaxLoginAttempts(security.getMaxLoginAttempts())
            .setLockoutDuration(security.getLockoutDuration())
            .setRequireTwoFactor(security.isRequireTwoFactor())
            .setPasswordMinLength(security.getPasswordMinLength())
            .setPasswordRequireSpecial(security.isPasswordRequireSpecial())
            .addAllIpWhitelist(security.getIpWhitelist() != null ? security.getIpWhitelist() : List.of())
            .addAllCorsOrigins(security.getCorsOrigins() != null ? security.getCorsOrigins() : List.of())
            .build();
    }

    private static AdminSystemNotificationsConfig toNotifications(SystemConfig.NotificationsConfig notifications) {
        return AdminSystemNotificationsConfig.newBuilder()
            .setEmailNotifications(notifications.isEmailNotifications())
            .setCriticalAlerts(notifications.isCriticalAlerts())
            .setWeeklyReports(notifications.isWeeklyReports())
            .setMaintenanceAlerts(notifications.isMaintenanceAlerts())
            .setSlackWebhook(stringValue(notifications.getSlackWebhook()))
            .setDiscordWebhook(stringValue(notifications.getDiscordWebhook()))
            .build();
    }

    private static AdminSystemPerformanceConfig toPerformance(SystemConfig.PerformanceConfig performance) {
        return AdminSystemPerformanceConfig.newBuilder()
            .setCacheTtl(performance.getCacheTtl())
            .setRateLimitRequests(performance.getRateLimitRequests())
            .setRateLimitWindow(performance.getRateLimitWindow())
            .setDatabaseConnectionPool(performance.getDatabaseConnectionPool())
            .setEnableCompression(performance.isEnableCompression())
            .setEnableCaching(performance.isEnableCaching())
            .build();
    }

    private static AdminSystemFeaturesConfig toFeatures(SystemConfig.FeaturesConfig features) {
        return AdminSystemFeaturesConfig.newBuilder()
            .setAnalyticsEnabled(features.isAnalyticsEnabled())
            .setAuditLoggingEnabled(features.isAuditLoggingEnabled())
            .setApiAccessEnabled(features.isApiAccessEnabled())
            .setBulkOperationsEnabled(features.isBulkOperationsEnabled())
            .setAdvancedFiltering(features.isAdvancedFiltering())
            .setRealTimeUpdates(features.isRealTimeUpdates())
            .build();
    }

    private static AdminSystemPrompt toPrompt(SystemPrompt prompt) {
        return AdminSystemPrompt.newBuilder()
            .setId(stringValue(prompt.getId()))
            .setPrompt(stringValue(prompt.getPrompt()))
            .setIsActive(prompt.isActive())
            .setCreatedAt(toTimestamp(prompt.getCreatedAt()))
            .setUpdatedAt(toTimestamp(prompt.getUpdatedAt()))
            .build();
    }

    private static UpdateSystemConfigRequest.GeneralConfigRequest fromGeneral(
        gg.modl.proto.modl.v1.UpdateSystemConfigRequest.GeneralConfigRequest general) {
        return new UpdateSystemConfigRequest.GeneralConfigRequest(
            general.getSystemName(),
            general.hasAdminEmail() ? general.getAdminEmail() : null,
            general.hasTimezone() ? general.getTimezone() : null,
            general.hasDefaultLanguage() ? general.getDefaultLanguage() : null,
            general.hasMaintenanceMode() ? general.getMaintenanceMode() : null,
            general.hasMaintenanceMessage() ? general.getMaintenanceMessage() : null
        );
    }

    private static UpdateSystemConfigRequest.LoggingConfigRequest fromLogging(
        gg.modl.proto.modl.v1.UpdateSystemConfigRequest.LoggingConfigRequest logging) {
        return new UpdateSystemConfigRequest.LoggingConfigRequest(
            logging.hasPm2LoggingEnabled() ? logging.getPm2LoggingEnabled() : null,
            logging.hasLogRetentionDays() ? logging.getLogRetentionDays() : null,
            logging.hasMaxLogSizePerDay() ? logging.getMaxLogSizePerDay() : null
        );
    }

    private static UpdateSystemConfigRequest.SecurityConfigRequest fromSecurity(
        gg.modl.proto.modl.v1.UpdateSystemConfigRequest.SecurityConfigRequest security) {
        return new UpdateSystemConfigRequest.SecurityConfigRequest(
            security.hasSessionTimeout() ? security.getSessionTimeout() : null,
            security.hasMaxLoginAttempts() ? security.getMaxLoginAttempts() : null,
            security.hasLockoutDuration() ? security.getLockoutDuration() : null,
            security.hasRequireTwoFactor() ? security.getRequireTwoFactor() : null,
            security.hasPasswordMinLength() ? security.getPasswordMinLength() : null,
            security.hasPasswordRequireSpecial() ? security.getPasswordRequireSpecial() : null,
            security.getIpWhitelistCount() > 0 ? new ArrayList<>(security.getIpWhitelistList()) : null,
            security.getCorsOriginsCount() > 0 ? new ArrayList<>(security.getCorsOriginsList()) : null
        );
    }

    private static UpdateSystemConfigRequest.NotificationsConfigRequest fromNotifications(
        gg.modl.proto.modl.v1.UpdateSystemConfigRequest.NotificationsConfigRequest notifications) {
        return new UpdateSystemConfigRequest.NotificationsConfigRequest(
            notifications.hasEmailNotifications() ? notifications.getEmailNotifications() : null,
            notifications.hasCriticalAlerts() ? notifications.getCriticalAlerts() : null,
            notifications.hasWeeklyReports() ? notifications.getWeeklyReports() : null,
            notifications.hasMaintenanceAlerts() ? notifications.getMaintenanceAlerts() : null,
            notifications.hasSlackWebhook() ? notifications.getSlackWebhook() : null,
            notifications.hasDiscordWebhook() ? notifications.getDiscordWebhook() : null
        );
    }

    private static UpdateSystemConfigRequest.PerformanceConfigRequest fromPerformance(
        gg.modl.proto.modl.v1.UpdateSystemConfigRequest.PerformanceConfigRequest performance) {
        return new UpdateSystemConfigRequest.PerformanceConfigRequest(
            performance.hasCacheTtl() ? performance.getCacheTtl() : null,
            performance.hasRateLimitRequests() ? performance.getRateLimitRequests() : null,
            performance.hasRateLimitWindow() ? performance.getRateLimitWindow() : null,
            performance.hasDatabaseConnectionPool() ? performance.getDatabaseConnectionPool() : null,
            performance.hasEnableCompression() ? performance.getEnableCompression() : null,
            performance.hasEnableCaching() ? performance.getEnableCaching() : null
        );
    }

    private static UpdateSystemConfigRequest.FeaturesConfigRequest fromFeatures(
        gg.modl.proto.modl.v1.UpdateSystemConfigRequest.FeaturesConfigRequest features) {
        return new UpdateSystemConfigRequest.FeaturesConfigRequest(
            features.hasAnalyticsEnabled() ? features.getAnalyticsEnabled() : null,
            features.hasAuditLoggingEnabled() ? features.getAuditLoggingEnabled() : null,
            features.hasApiAccessEnabled() ? features.getApiAccessEnabled() : null,
            features.hasBulkOperationsEnabled() ? features.getBulkOperationsEnabled() : null,
            features.hasAdvancedFiltering() ? features.getAdvancedFiltering() : null,
            features.hasRealTimeUpdates() ? features.getRealTimeUpdates() : null
        );
    }
}
