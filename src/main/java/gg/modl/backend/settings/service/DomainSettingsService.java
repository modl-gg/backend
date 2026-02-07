package gg.modl.backend.settings.service;

import gg.modl.backend.cors.DynamicCorsConfigurationSource;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.domain.external.CloudflareClient;
import gg.modl.backend.server.ServerField;
import gg.modl.backend.server.data.CustomDomainStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DomainSettings;
import gg.modl.backend.settings.data.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainSettingsService {
    private static final String SETTINGS_TYPE_DOMAIN = "domain";

    private final DynamicMongoTemplateProvider mongoProvider;
    private final CloudflareClient cloudflareClient;
    private final DynamicCorsConfigurationSource corsConfigurationSource;

    public DomainSettings getDomainSettings(Server server, String requestHost) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_DOMAIN));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

        String modlSubdomainUrl = "https://" + server.getCustomDomain() + ".modl.gg";
        boolean accessingFromCustomDomain = false;

        if (settings == null || settings.getData() == null) {
            // Check if Server document has custom domain data that needs to be migrated
            if (server.getCustomDomainOverride() != null && !server.getCustomDomainOverride().isEmpty()) {
                return migrateAndReturnDomainSettings(server, template, query, requestHost, modlSubdomainUrl);
            }

            return DomainSettings.builder()
                    .customDomain(null)
                    .status(null)
                    .accessingFromCustomDomain(false)
                    .modlSubdomainUrl(modlSubdomainUrl)
                    .build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) settings.getData();
        String customDomain = getStringValue(data, "customDomain");

        // If settings collection has no domain but Server document does, migrate it
        if ((customDomain == null || customDomain.isEmpty()) &&
            server.getCustomDomainOverride() != null && !server.getCustomDomainOverride().isEmpty()) {
            return migrateAndReturnDomainSettings(server, template, query, requestHost, modlSubdomainUrl);
        }

        if (customDomain != null && !customDomain.isEmpty() && requestHost != null) {
            accessingFromCustomDomain = requestHost.equalsIgnoreCase(customDomain);
        }

        DomainSettings.DomainStatus status = null;
        if (customDomain != null && !customDomain.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> statusData = (Map<String, Object>) data.get("status");
            if (statusData != null) {
                status = DomainSettings.DomainStatus.builder()
                        .domain(getStringValue(statusData, "domain"))
                        .status(getStringValue(statusData, "status"))
                        .cnameConfigured(getBooleanValue(statusData, "cnameConfigured"))
                        .sslStatus(getStringValue(statusData, "sslStatus"))
                        .lastChecked(getStringValue(statusData, "lastChecked"))
                        .error(getStringValue(statusData, "error"))
                        .build();
            }
        }

        return DomainSettings.builder()
                .customDomain(customDomain)
                .status(status)
                .accessingFromCustomDomain(accessingFromCustomDomain)
                .modlSubdomainUrl(modlSubdomainUrl)
                .build();
    }

    private DomainSettings migrateAndReturnDomainSettings(Server server, MongoTemplate template, Query query,
                                                           String requestHost, String modlSubdomainUrl) {
        String customDomain = server.getCustomDomainOverride();
        CustomDomainStatus serverStatus = server.getCustomDomainStatus();
        String cloudflareId = server.getCustomDomainCloudflareId();
        String error = server.getCustomDomainError();
        Date lastChecked = server.getCustomDomainLastChecked();

        String statusString = serverStatus != null ? serverStatus.name() : "pending";
        boolean cnameConfigured = "active".equals(statusString);

        DomainSettings.DomainStatus status = DomainSettings.DomainStatus.builder()
                .domain(customDomain)
                .status(statusString)
                .cnameConfigured(cnameConfigured)
                .sslStatus(cnameConfigured ? "active" : "pending")
                .lastChecked(lastChecked != null ? lastChecked.toInstant().toString() : Instant.now().toString())
                .error(error)
                .build();

        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("domain", status.getDomain());
        statusMap.put("status", status.getStatus());
        statusMap.put("cnameConfigured", status.isCnameConfigured());
        statusMap.put("sslStatus", status.getSslStatus());
        statusMap.put("lastChecked", status.getLastChecked());
        statusMap.put("error", status.getError());

        Map<String, Object> data = new HashMap<>();
        data.put("customDomain", customDomain);
        data.put("status", statusMap);
        data.put("cloudflareHostnameId", cloudflareId);

        Update update = new Update()
                .set("type", SETTINGS_TYPE_DOMAIN)
                .set("data", data);

        template.upsert(query, update, Settings.class, CollectionName.SETTINGS);

        // Invalidate CORS cache to ensure migrated domain is recognized
        corsConfigurationSource.invalidateCache(customDomain);

        boolean accessingFromCustomDomain = requestHost != null && requestHost.equalsIgnoreCase(customDomain);

        return DomainSettings.builder()
                .customDomain(customDomain)
                .status(status)
                .accessingFromCustomDomain(accessingFromCustomDomain)
                .modlSubdomainUrl(modlSubdomainUrl)
                .build();
    }

    public DomainSettings configureDomain(Server server, String customDomain) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query settingsQuery = new Query(Criteria.where("type").is(SETTINGS_TYPE_DOMAIN));

        // Check if the domain is the same as currently configured
        String currentDomain = server.getCustomDomainOverride();
        if (currentDomain == null) {
            Settings existingSettings = template.findOne(settingsQuery, Settings.class, CollectionName.SETTINGS);
            if (existingSettings != null && existingSettings.getData() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) existingSettings.getData();
                currentDomain = getStringValue(data, "customDomain");
            }
        }

        if (currentDomain != null && currentDomain.equalsIgnoreCase(customDomain)) {
            throw new IllegalArgumentException("This domain is already configured. Please verify the existing configuration or remove it first.");
        }

        CloudflareClient.CustomHostnameResult existingHostname = cloudflareClient.findCustomHostnameByName(customDomain);
        if (existingHostname != null) {
            cloudflareClient.deleteCustomHostname(existingHostname.id());
        }

        CloudflareClient.CustomHostnameResult cfResult = cloudflareClient.createCustomHostname(customDomain);

        String initialStatus = "pending";
        String sslStatus = "pending";
        String error = null;
        String cloudflareHostnameId = null;

        if (cfResult == null) {
            initialStatus = "error";
            sslStatus = "error";
            error = "Failed to create custom hostname in Cloudflare. Please check your configuration.";
            log.error("Failed to create Cloudflare custom hostname for domain: {}", customDomain);
        } else {
            cloudflareHostnameId = cfResult.id();
            initialStatus = mapCloudflareStatus(cfResult.status());
            if (cfResult.ssl() != null) {
                sslStatus = mapCloudflareStatus(cfResult.ssl().status());
            }
        }

        DomainSettings.DomainStatus status = DomainSettings.DomainStatus.builder()
                .domain(customDomain)
                .status(initialStatus)
                .cnameConfigured(false)
                .sslStatus(sslStatus)
                .lastChecked(Instant.now().toString())
                .error(error)
                .build();

        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("domain", status.getDomain());
        statusMap.put("status", status.getStatus());
        statusMap.put("cnameConfigured", status.isCnameConfigured());
        statusMap.put("sslStatus", status.getSslStatus());
        statusMap.put("lastChecked", status.getLastChecked());
        statusMap.put("error", status.getError());

        Map<String, Object> data = new HashMap<>();
        data.put("customDomain", customDomain);
        data.put("status", statusMap);
        data.put("cloudflareHostnameId", cloudflareHostnameId);

        Update settingsUpdate = new Update()
                .set("type", SETTINGS_TYPE_DOMAIN)
                .set("data", data);

        template.upsert(settingsQuery, settingsUpdate, Settings.class, CollectionName.SETTINGS);

        // Update the main Server document in the global database
        updateServerDocument(server.getId(), customDomain, initialStatus, cloudflareHostnameId, error);

        return DomainSettings.builder()
                .customDomain(customDomain)
                .status(status)
                .accessingFromCustomDomain(false)
                .modlSubdomainUrl("https://" + server.getCustomDomain() + ".modl.gg")
                .build();
    }

    private void updateServerDocument(String serverId, String customDomain, String status,
                                       String cloudflareHostnameId, String error) {
        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query serverQuery = new Query(Criteria.where("_id").is(serverId));

        CustomDomainStatus domainStatus = switch (status) {
            case "active" -> CustomDomainStatus.active;
            case "error" -> CustomDomainStatus.error;
            case "verifying" -> CustomDomainStatus.verifying;
            default -> CustomDomainStatus.pending;
        };

        Update serverUpdate = new Update()
                .set(ServerField.CUSTOM_DOMAIN, customDomain)
                .set(ServerField.CUSTOM_DOMAIN_STATUS, domainStatus.name())
                .set("customDomain_cloudflareId", cloudflareHostnameId)
                .set("customDomain_lastChecked", new Date())
                .set("customDomain_error", error)
                .set("updatedAt", new Date());

        globalDb.updateFirst(serverQuery, serverUpdate, Server.class, CollectionName.MODL_SERVERS);

        // Invalidate CORS cache so the new domain status is recognized immediately
        corsConfigurationSource.invalidateCache(customDomain);
        log.debug("Invalidated CORS cache for domain: {}", customDomain);
    }

    public DomainSettings verifyDomain(Server server, String domain) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_DOMAIN));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

        if (settings == null || settings.getData() == null) {
            throw new IllegalStateException("No domain configured");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) settings.getData();
        String configuredDomain = getStringValue(data, "customDomain");
        String cloudflareHostnameId = getStringValue(data, "cloudflareHostnameId");

        if (!domain.equalsIgnoreCase(configuredDomain)) {
            throw new IllegalArgumentException("Domain does not match configured domain");
        }

        String verifiedStatus = "pending";
        String sslStatus = "pending";
        boolean cnameConfigured = false;
        String error = null;

        CloudflareClient.CustomHostnameResult cfResult = null;

        if (cloudflareHostnameId != null && !cloudflareHostnameId.isEmpty()) {
            cfResult = cloudflareClient.getCustomHostname(cloudflareHostnameId);
        } else {
            cfResult = cloudflareClient.findCustomHostnameByName(domain);
            if (cfResult != null) {
                cloudflareHostnameId = cfResult.id();
            }
        }

        if (cfResult != null) {
            verifiedStatus = mapCloudflareStatus(cfResult.status());
            cnameConfigured = "active".equals(verifiedStatus);

            if (cfResult.ssl() != null) {
                sslStatus = mapCloudflareStatus(cfResult.ssl().status());
            }

            if ("blocked".equals(cfResult.status()) || "moved".equals(cfResult.status())) {
                error = "Domain verification failed. Status: " + cfResult.status();
            }
        } else {
            verifiedStatus = "error";
            sslStatus = "error";
            error = "Custom hostname not found in Cloudflare. Please reconfigure the domain.";
            log.warn("Custom hostname not found in Cloudflare for domain: {}", domain);
        }

        DomainSettings.DomainStatus status = DomainSettings.DomainStatus.builder()
                .domain(domain)
                .status(verifiedStatus)
                .cnameConfigured(cnameConfigured)
                .sslStatus(sslStatus)
                .lastChecked(Instant.now().toString())
                .error(error)
                .build();

        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("domain", status.getDomain());
        statusMap.put("status", status.getStatus());
        statusMap.put("cnameConfigured", status.isCnameConfigured());
        statusMap.put("sslStatus", status.getSslStatus());
        statusMap.put("lastChecked", status.getLastChecked());
        statusMap.put("error", status.getError());

        data.put("status", statusMap);
        if (cloudflareHostnameId != null) {
            data.put("cloudflareHostnameId", cloudflareHostnameId);
        }

        Update update = new Update()
                .set("data", data);

        template.updateFirst(query, update, Settings.class, CollectionName.SETTINGS);

        // Update the main Server document in the global database
        updateServerDocument(server.getId(), domain, verifiedStatus, cloudflareHostnameId, error);

        return DomainSettings.builder()
                .customDomain(domain)
                .status(status)
                .accessingFromCustomDomain(false)
                .modlSubdomainUrl("https://" + server.getCustomDomain() + ".modl.gg")
                .build();
    }

    public void removeDomain(Server server) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_DOMAIN));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

        String customDomain = null;

        if (settings != null && settings.getData() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) settings.getData();
            String cloudflareHostnameId = getStringValue(data, "cloudflareHostnameId");
            customDomain = getStringValue(data, "customDomain");

            if (cloudflareHostnameId != null && !cloudflareHostnameId.isEmpty()) {
                boolean deleted = cloudflareClient.deleteCustomHostname(cloudflareHostnameId);
                if (!deleted) {
                    log.warn("Failed to delete Cloudflare custom hostname for domain: {}", customDomain);
                }
            } else if (customDomain != null && !customDomain.isEmpty()) {
                CloudflareClient.CustomHostnameResult cfResult = cloudflareClient.findCustomHostnameByName(customDomain);
                if (cfResult != null) {
                    cloudflareClient.deleteCustomHostname(cfResult.id());
                }
            }
        }

        // Also check if domain is stored in Server document
        if (customDomain == null && server.getCustomDomainOverride() != null) {
            customDomain = server.getCustomDomainOverride();
        }

        template.remove(query, Settings.class, CollectionName.SETTINGS);

        // Clear custom domain fields from the main Server document
        clearServerDomainFields(server.getId());

        // Invalidate CORS cache for the removed domain
        if (customDomain != null && !customDomain.isEmpty()) {
            corsConfigurationSource.invalidateCache(customDomain);
            log.debug("Invalidated CORS cache for removed domain: {}", customDomain);
        }
    }

    private void clearServerDomainFields(String serverId) {
        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query serverQuery = new Query(Criteria.where("_id").is(serverId));

        Update serverUpdate = new Update()
                .unset(ServerField.CUSTOM_DOMAIN)
                .unset(ServerField.CUSTOM_DOMAIN_STATUS)
                .unset("customDomain_cloudflareId")
                .unset("customDomain_lastChecked")
                .unset("customDomain_error")
                .set("updatedAt", new Date());

        globalDb.updateFirst(serverQuery, serverUpdate, Server.class, CollectionName.MODL_SERVERS);
    }

    private String mapCloudflareStatus(String cfStatus) {
        if (cfStatus == null) {
            return "pending";
        }
        return switch (cfStatus.toLowerCase()) {
            case "active" -> "active";
            case "pending", "pending_validation", "pending_issuance", "pending_deployment", "initializing" -> "pending";
            case "pending_deletion", "deleted" -> "pending";
            case "blocked", "moved" -> "error";
            default -> "pending";
        };
    }

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof String ? (String) value : null;
    }

    private boolean getBooleanValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Boolean ? (Boolean) value : false;
    }
}
