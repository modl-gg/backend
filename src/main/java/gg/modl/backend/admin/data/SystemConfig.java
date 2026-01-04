package gg.modl.backend.admin.data;

import gg.modl.backend.ModlConstants;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Document(collection = "system_config")
public class SystemConfig {
    @Id
    private String id;
    private String configId = "main_config";

    private GeneralConfig general = new GeneralConfig();
    private LoggingConfig logging = new LoggingConfig();
    private SecurityConfig security = new SecurityConfig();
    private NotificationsConfig notifications = new NotificationsConfig();
    private PerformanceConfig performance = new PerformanceConfig();
    private FeaturesConfig features = new FeaturesConfig();

    private Date createdAt = new Date();
    private Date updatedAt = new Date();

    @Data
    public static class GeneralConfig {
        private String systemName = "modl Admin";
        private String adminEmail = ModlConstants.Email.ADMIN;
        private String timezone = "UTC";
        private String defaultLanguage = "en";
        private boolean maintenanceMode = false;
        private String maintenanceMessage = "System under maintenance. Please check back later.";
    }

    @Data
    public static class LoggingConfig {
        private boolean pm2LoggingEnabled = true;
        private int logRetentionDays = 30;
        private int maxLogSizePerDay = 1000000;
    }

    @Data
    public static class SecurityConfig {
        private int sessionTimeout = 60;
        private int maxLoginAttempts = 5;
        private int lockoutDuration = 15;
        private boolean requireTwoFactor = false;
        private int passwordMinLength = 8;
        private boolean passwordRequireSpecial = false;
        private List<String> ipWhitelist = new ArrayList<>();
        private List<String> corsOrigins = new ArrayList<>(List.of(ModlConstants.Domain.HTTPS_ADMIN));
    }

    @Data
    public static class NotificationsConfig {
        private boolean emailNotifications = true;
        private boolean criticalAlerts = true;
        private boolean weeklyReports = true;
        private boolean maintenanceAlerts = true;
        private String slackWebhook = "";
        private String discordWebhook = "";
    }

    @Data
    public static class PerformanceConfig {
        private int cacheTtl = 300;
        private int rateLimitRequests = 100;
        private int rateLimitWindow = 60;
        private int databaseConnectionPool = 10;
        private boolean enableCompression = true;
        private boolean enableCaching = true;
    }

    @Data
    public static class FeaturesConfig {
        private boolean analyticsEnabled = true;
        private boolean auditLoggingEnabled = true;
        private boolean apiAccessEnabled = true;
        private boolean bulkOperationsEnabled = true;
        private boolean advancedFiltering = true;
        private boolean realTimeUpdates = true;
    }
}
