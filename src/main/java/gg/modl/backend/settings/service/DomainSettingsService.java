package gg.modl.backend.settings.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
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
        
        // Check if accessing from custom domain
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

        DomainSettings.DomainStatus status = DomainSettings.DomainStatus.builder()
                .domain(customDomain)
                .status("pending")
                .cnameConfigured(false)
                .sslStatus("pending")
                .lastChecked(Instant.now().toString())
                .error(null)
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

        if (!domain.equalsIgnoreCase(configuredDomain)) {
            throw new IllegalArgumentException("Domain does not match configured domain");
        }

        // For now, we'll simulate verification by setting status to active
        // In production, this would involve actual DNS verification via Cloudflare API
        DomainSettings.DomainStatus status = DomainSettings.DomainStatus.builder()
                .domain(domain)
                .status("active")
                .cnameConfigured(true)
                .sslStatus("active")
                .lastChecked(Instant.now().toString())
                .error(null)
                .build();

        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("domain", status.getDomain());
        statusMap.put("status", status.getStatus());
        statusMap.put("cnameConfigured", status.isCnameConfigured());
        statusMap.put("sslStatus", status.getSslStatus());
        statusMap.put("lastChecked", status.getLastChecked());
        statusMap.put("error", status.getError());

        data.put("status", statusMap);

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
        template.remove(query, Settings.class, CollectionName.SETTINGS);
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
