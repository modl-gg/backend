package gg.modl.backend.settings.service;

import gg.modl.backend.cors.DynamicCorsConfigurationSource;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.domain.external.CloudflareClient;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DomainSettings;
import gg.modl.backend.settings.data.Settings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class DomainSettingsService extends AbstractSettingsService {
    private static final String SETTINGS_TYPE_DOMAIN = "domain";

    private final ServerMongoRepository serverRepository;
    private final CloudflareClient cloudflareClient;
    private final DynamicCorsConfigurationSource corsConfigurationSource;
    private final CustomDomainAccessService customDomainAccessService;

    public DomainSettingsService(
            SettingsMongoRepository settingsRepository,
            ServerMongoRepository serverRepository,
            CloudflareClient cloudflareClient,
            DynamicCorsConfigurationSource corsConfigurationSource,
            CustomDomainAccessService customDomainAccessService
    ) {
        super(settingsRepository);
        this.serverRepository = serverRepository;
        this.cloudflareClient = cloudflareClient;
        this.corsConfigurationSource = corsConfigurationSource;
        this.customDomainAccessService = customDomainAccessService;
    }

    public DomainSettings getDomainSettings(Server server, String requestHost) {
        Settings settings = findSettings(server, SETTINGS_TYPE_DOMAIN).orElse(null);

        String modlSubdomainUrl = "https://" + server.getCustomDomain() + ".modl.gg";
        boolean accessingFromCustomDomain = false;
        boolean canManageCustomDomain = customDomainAccessService.canManageCustomDomain(server);

        if (settings == null || settings.getData() == null) {
            return DomainSettings.builder()
                    .customDomain(null)
                    .status(null)
                    .accessingFromCustomDomain(false)
                    .modlSubdomainUrl(modlSubdomainUrl)
                    .canManageCustomDomain(canManageCustomDomain)
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
                .canManageCustomDomain(canManageCustomDomain)
                .build();
    }

    public DomainSettings configureDomain(Server server, String customDomain) {
        if (customDomain.toLowerCase().endsWith("modl.gg")) {
            throw new IllegalArgumentException("modl.gg domains cannot be used as custom domains.");
        }

        String currentDomain = server.getCustomDomainOverride();
        if (currentDomain == null) {
            Settings existingSettings = findSettings(server, SETTINGS_TYPE_DOMAIN).orElse(null);
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

        upsertSettings(server, SETTINGS_TYPE_DOMAIN, data);

        updateServerDocument(server.getId(), customDomain, initialStatus, cloudflareHostnameId, error);

        return DomainSettings.builder()
                .customDomain(customDomain)
                .status(status)
                .accessingFromCustomDomain(false)
                .modlSubdomainUrl("https://" + server.getCustomDomain() + ".modl.gg")
                .canManageCustomDomain(customDomainAccessService.canManageCustomDomain(server))
                .build();
    }

    private void updateServerDocument(String serverId, String customDomain, String status,
                                       String cloudflareHostnameId, String error) {
        serverRepository.updateCustomDomain(serverId, customDomain, status, cloudflareHostnameId, error);
        corsConfigurationSource.invalidateCache(customDomain);
        log.debug("Invalidated CORS cache for domain: {}", customDomain);
    }

    public DomainSettings verifyDomain(Server server, String domain) {
        Settings settings = findSettings(server, SETTINGS_TYPE_DOMAIN).orElse(null);

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

        updateDataSettings(server, SETTINGS_TYPE_DOMAIN, data);

        updateServerDocument(server.getId(), domain, verifiedStatus, cloudflareHostnameId, error);

        return DomainSettings.builder()
                .customDomain(domain)
                .status(status)
                .accessingFromCustomDomain(false)
                .modlSubdomainUrl("https://" + server.getCustomDomain() + ".modl.gg")
                .canManageCustomDomain(customDomainAccessService.canManageCustomDomain(server))
                .build();
    }

    public void removeDomain(Server server) {
        Settings settings = findSettings(server, SETTINGS_TYPE_DOMAIN).orElse(null);

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

        if (customDomain == null && server.getCustomDomainOverride() != null) {
            customDomain = server.getCustomDomainOverride();
        }

        removeSettings(server, SETTINGS_TYPE_DOMAIN);

        clearServerDomainFields(server.getId());

        if (customDomain != null && !customDomain.isEmpty()) {
            corsConfigurationSource.invalidateCache(customDomain);
            log.debug("Invalidated CORS cache for removed domain: {}", customDomain);
        }
    }

    private void clearServerDomainFields(String serverId) {
        serverRepository.clearCustomDomain(serverId);
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
