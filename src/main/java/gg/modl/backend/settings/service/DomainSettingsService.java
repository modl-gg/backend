package gg.modl.backend.settings.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.domain.external.CloudflareClient;
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
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainSettingsService {
    private static final String SETTINGS_TYPE_DOMAIN = "domain";

    private final DynamicMongoTemplateProvider mongoProvider;
    private final CloudflareClient cloudflareClient;

    public DomainSettings getDomainSettings(Server server, String requestHost) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_DOMAIN));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

        String modlSubdomainUrl = "https://" + server.getCustomDomain() + ".modl.gg";
        boolean accessingFromCustomDomain = false;

        if (settings == null || settings.getData() == null) {
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

    public DomainSettings configureDomain(Server server, String customDomain) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_DOMAIN));

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
            log.info("Created Cloudflare custom hostname for domain: {} with ID: {}", customDomain, cloudflareHostnameId);
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

        Update update = new Update()
                .set("type", SETTINGS_TYPE_DOMAIN)
                .set("data", data);

        template.upsert(query, update, Settings.class, CollectionName.SETTINGS);

        return DomainSettings.builder()
                .customDomain(customDomain)
                .status(status)
                .accessingFromCustomDomain(false)
                .modlSubdomainUrl("https://" + server.getCustomDomain() + ".modl.gg")
                .build();
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

            log.info("Verified Cloudflare custom hostname for domain: {} - status: {}, ssl: {}",
                    domain, cfResult.status(), cfResult.ssl() != null ? cfResult.ssl().status() : "unknown");
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

        if (settings != null && settings.getData() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) settings.getData();
            String cloudflareHostnameId = getStringValue(data, "cloudflareHostnameId");
            String customDomain = getStringValue(data, "customDomain");

            if (cloudflareHostnameId != null && !cloudflareHostnameId.isEmpty()) {
                boolean deleted = cloudflareClient.deleteCustomHostname(cloudflareHostnameId);
                if (deleted) {
                    log.info("Deleted Cloudflare custom hostname for domain: {}", customDomain);
                } else {
                    log.warn("Failed to delete Cloudflare custom hostname for domain: {}", customDomain);
                }
            } else if (customDomain != null && !customDomain.isEmpty()) {
                CloudflareClient.CustomHostnameResult cfResult = cloudflareClient.findCustomHostnameByName(customDomain);
                if (cfResult != null) {
                    boolean deleted = cloudflareClient.deleteCustomHostname(cfResult.id());
                    if (deleted) {
                        log.info("Deleted Cloudflare custom hostname for domain: {}", customDomain);
                    }
                }
            }
        }

        template.remove(query, Settings.class, CollectionName.SETTINGS);
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
