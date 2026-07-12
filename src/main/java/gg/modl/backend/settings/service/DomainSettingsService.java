package gg.modl.backend.settings.service;

import gg.modl.backend.infrastructure.cors.DynamicCorsConfigurationSource;
import gg.modl.backend.database.mongo.repository.ServerCustomDomainRepository;
import gg.modl.backend.cloudflare.external.CloudflareClient;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.CustomDomainStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DomainSettings;
import java.net.IDN;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DomainSettingsService {
    private final ServerCustomDomainRepository serverCustomDomainRepository;
    private final CloudflareClient cloudflareClient;
    private final DynamicCorsConfigurationSource corsConfigurationSource;
    private final CustomDomainAccessService customDomainAccessService;
    private final VersionedSettingsSupport<Map<String, Object>> support;
    private static final String SETTINGS_TYPE_DOMAIN = "domain";
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile("^(?=.{1,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\\.(?!-)[a-z0-9-]{1,63}(?<!-))+$");
    private static final Pattern IPV4_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Set<String> RESERVED_SUFFIXES = Set.of(
        "modl.gg", "modl.top", "localhost", "local", "internal", "test", "example", "invalid"
    );

    public DomainSettingsService(
        SettingsDocumentService settingsDocumentService,
        ServerCustomDomainRepository serverCustomDomainRepository,
        CloudflareClient cloudflareClient,
        DynamicCorsConfigurationSource corsConfigurationSource,
        CustomDomainAccessService customDomainAccessService
    ) {
        this.serverCustomDomainRepository = serverCustomDomainRepository;
        this.cloudflareClient = cloudflareClient;
        this.corsConfigurationSource = corsConfigurationSource;
        this.customDomainAccessService = customDomainAccessService;
        this.support = VersionedSettingsSupport.<Map<String, Object>>of(
            settingsDocumentService, SETTINGS_TYPE_DOMAIN, LinkedHashMap::new);
    }

    public DomainSettings getDomainSettings(Server server, String requestHost) {
        Map<String, Object> data = support.get(server);

        String modlSubdomainUrl = "https://" + server.getCustomDomain() + ".modl.gg";
        boolean canManageCustomDomain = customDomainAccessService.canManageCustomDomain(server);

        String customDomain = getStringValue(data, "customDomain");
        boolean accessingFromCustomDomain = customDomain != null && !customDomain.isEmpty()
                                            && requestHost != null && requestHost.equalsIgnoreCase(customDomain);

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

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof String string ? string : null;
    }

    private boolean getBooleanValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Boolean bool ? bool : false;
    }

    public DomainSettings configureDomain(Server server, String customDomain) {
        customDomain = normalizeAndValidateCustomDomain(customDomain);

        VersionedSettings<Map<String, Object>> state = support.state(server);
        Map<String, Object> current = state.data();

        String currentDomain = extractCurrentDomain(server, current);
        if (currentDomain != null && currentDomain.equalsIgnoreCase(customDomain)) {
            throw new ConflictException("This domain is already configured. Please verify the existing configuration or remove it first.");
        }

        String currentCloudflareHostnameId = extractCurrentCloudflareHostnameId(server, current);
        CloudflareClient.CustomHostnameResult existingHostname = cloudflareClient.findCustomHostnameByName(customDomain);
        if (existingHostname != null) {
            if (currentCloudflareHostnameId == null || !existingHostname.id().equals(currentCloudflareHostnameId)) {
                throw new ConflictException("This domain is already configured by another server.");
            }
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

        Map<String, Object> data = new HashMap<>();
        data.put("customDomain", customDomain);
        data.put("status", buildDomainStatusMap(status));
        data.put("cloudflareHostnameId", cloudflareHostnameId);

        support.save(server, state.version(), data);

        updateServerDocument(server.getId(), customDomain, initialStatus, cloudflareHostnameId, error);

        return buildDomainSettingsResponse(server, customDomain, status);
    }

    private void updateServerDocument(String serverId, String customDomain, String status,
                                      String cloudflareHostnameId, String error) {
        serverCustomDomainRepository.updateCustomDomain(serverId, customDomain, mapDomainStatus(status), cloudflareHostnameId, error);
        corsConfigurationSource.invalidateCache(customDomain);
        log.debug("Invalidated CORS cache for domain: {}", customDomain);
    }

    private CustomDomainStatus mapDomainStatus(String status) {
        return switch (status) {
            case "active" -> CustomDomainStatus.ACTIVE;
            case "error" -> CustomDomainStatus.ERROR;
            case "verifying" -> CustomDomainStatus.VERIFYING;
            default -> CustomDomainStatus.PENDING;
        };
    }

    private String extractCurrentDomain(Server server, Map<String, Object> current) {
        String currentDomain = server.getCustomDomainOverride();
        if (currentDomain == null) {
            currentDomain = getStringValue(current, "customDomain");
        }
        return currentDomain;
    }

    private String extractCurrentCloudflareHostnameId(Server server, Map<String, Object> current) {
        String currentCloudflareHostnameId = server.getCustomDomainCloudflareId();
        if (currentCloudflareHostnameId == null) {
            currentCloudflareHostnameId = getStringValue(current, "cloudflareHostnameId");
        }
        return currentCloudflareHostnameId;
    }

    private Map<String, Object> buildDomainStatusMap(DomainSettings.DomainStatus status) {
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("domain", status.getDomain());
        statusMap.put("status", status.getStatus());
        statusMap.put("cnameConfigured", status.isCnameConfigured());
        statusMap.put("sslStatus", status.getSslStatus());
        statusMap.put("lastChecked", status.getLastChecked());
        statusMap.put("error", status.getError());
        return statusMap;
    }

    private DomainSettings buildDomainSettingsResponse(Server server, String customDomain,
                                                       DomainSettings.DomainStatus status) {
        return DomainSettings.builder()
            .customDomain(customDomain)
            .status(status)
            .accessingFromCustomDomain(false)
            .modlSubdomainUrl("https://" + server.getCustomDomain() + ".modl.gg")
            .canManageCustomDomain(customDomainAccessService.canManageCustomDomain(server))
            .build();
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

    public DomainSettings verifyDomain(Server server, String domain) {
        domain = normalizeAndValidateCustomDomain(domain);
        VersionedSettings<Map<String, Object>> state = support.state(server);
        Map<String, Object> data = state.data();

        if (data.isEmpty()) {
            throw new ResourceNotFoundException("No domain configured");
        }

        String configuredDomain = getStringValue(data, "customDomain");
        String cloudflareHostnameId = getStringValue(data, "cloudflareHostnameId");

        if (!domain.equalsIgnoreCase(configuredDomain)) {
            throw new ValidationException("Domain does not match configured domain");
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

        data.put("status", buildDomainStatusMap(status));
        if (cloudflareHostnameId != null) {
            data.put("cloudflareHostnameId", cloudflareHostnameId);
        }

        support.save(server, state.version(), data);

        updateServerDocument(server.getId(), domain, verifiedStatus, cloudflareHostnameId, error);

        return buildDomainSettingsResponse(server, domain, status);
    }

    private String normalizeAndValidateCustomDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            throw new ValidationException("Custom domain is required");
        }
        String trimmed = domain.trim();
        if (trimmed.endsWith(".")) {
            throw new ValidationException("Custom domain must not include a trailing dot");
        }
        String ascii;
        try {
            ascii = IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("Invalid custom domain", exception);
        }
        if (!HOSTNAME_PATTERN.matcher(ascii).matches()
            || IPV4_PATTERN.matcher(ascii).matches()
            || ascii.contains(":")
            || isReservedDomain(ascii)) {
            throw new ValidationException("This custom domain cannot be used.");
        }
        return ascii;
    }

    private boolean isReservedDomain(String domain) {
        for (String suffix : RESERVED_SUFFIXES) {
            if (domain.equals(suffix) || domain.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    public void removeDomain(Server server) {
        Map<String, Object> data = support.get(server);

        String customDomain = null;

        if (!data.isEmpty()) {
            String cloudflareHostnameId = getStringValue(data, "cloudflareHostnameId");
            customDomain = getStringValue(data, "customDomain");

            if (cloudflareHostnameId != null && !cloudflareHostnameId.isEmpty()) {
                boolean deleted = cloudflareClient.deleteCustomHostname(cloudflareHostnameId);
                if (!deleted) {
                    log.warn("Failed to delete Cloudflare custom hostname for domain: {}", customDomain);
                }
            }
        }

        if (customDomain == null && server.getCustomDomainOverride() != null) {
            customDomain = server.getCustomDomainOverride();
        }

        support.delete(server);

        clearServerDomainFields(server.getId());

        if (customDomain != null && !customDomain.isEmpty()) {
            corsConfigurationSource.invalidateCache(customDomain);
            log.debug("Invalidated CORS cache for removed domain: {}", customDomain);
        }
    }

    private void clearServerDomainFields(String serverId) {
        serverCustomDomainRepository.clearCustomDomain(serverId);
    }
}
