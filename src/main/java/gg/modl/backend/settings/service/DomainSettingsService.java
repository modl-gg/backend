package gg.modl.backend.settings.service;

import gg.modl.backend.cloudflare.config.CloudflareConfiguration;
import gg.modl.backend.cloudflare.external.CloudflareClient;
import gg.modl.backend.database.mongo.repository.ServerCustomDomainRepository;
import gg.modl.backend.infrastructure.config.ModlCorsProperties;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.exception.ServiceUnavailableException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.CustomDomainStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DomainSettings;
import java.net.IDN;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DomainSettingsService {
    private final ServerCustomDomainRepository serverCustomDomainRepository;
    private final CloudflareClient cloudflareClient;
    private final CloudflareConfiguration cloudflareConfiguration;
    private final CustomDomainAccessService customDomainAccessService;
    private final CustomDomainStatusMapper statusMapper;
    private final CustomDomainStateWriter stateWriter;
    private final CustomDomainLockRegistry lockRegistry;
    private final SettingsDocumentService settingsDocumentService;
    private final Set<String> reservedSuffixes;
    private static final String SETTINGS_TYPE_DOMAIN = "domain";
    private static final String DOMAIN_UNAVAILABLE_MESSAGE = "This domain is not available.";
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile("^(?=.{1,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\\.(?!-)[a-z0-9-]{1,63}(?<!-))+$");
    private static final Pattern IPV4_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Set<String> RESERVED_SUFFIXES = Set.of(
        "modl.gg", "modl.top", "localhost", "local", "internal", "test", "example", "invalid"
    );

    public DomainSettingsService(
        SettingsDocumentService settingsDocumentService,
        ServerCustomDomainRepository serverCustomDomainRepository,
        CloudflareClient cloudflareClient,
        CloudflareConfiguration cloudflareConfiguration,
        CustomDomainAccessService customDomainAccessService,
        CustomDomainStatusMapper statusMapper,
        CustomDomainStateWriter stateWriter,
        CustomDomainLockRegistry lockRegistry,
        ModlCorsProperties corsProperties
    ) {
        this.serverCustomDomainRepository = serverCustomDomainRepository;
        this.cloudflareClient = cloudflareClient;
        this.cloudflareConfiguration = cloudflareConfiguration;
        this.customDomainAccessService = customDomainAccessService;
        this.statusMapper = statusMapper;
        this.stateWriter = stateWriter;
        this.lockRegistry = lockRegistry;
        this.settingsDocumentService = settingsDocumentService;
        this.reservedSuffixes = buildReservedSuffixes(corsProperties);
    }

    private static Set<String> buildReservedSuffixes(ModlCorsProperties corsProperties) {
        Set<String> combined = new HashSet<>(RESERVED_SUFFIXES);
        Arrays.stream(corsProperties.getAppDomains().split(","))
            .map(String::trim)
            .filter(suffix -> !suffix.isBlank())
            .map(suffix -> suffix.toLowerCase(Locale.ROOT))
            .forEach(combined::add);
        return Set.copyOf(combined);
    }

    public DomainSettings getDomainSettings(Server server, String requestHost) {
        String customDomain = server.getCustomDomainOverride();
        boolean accessingFromCustomDomain = customDomain != null && !customDomain.isEmpty()
                                            && requestHost != null && requestHost.equalsIgnoreCase(customDomain);
        DomainSettings.DomainStatus status = customDomain == null || customDomain.isEmpty()
                                             ? null : statusFromServer(server);
        return buildResponse(server, customDomain, status, accessingFromCustomDomain);
    }

    public DomainSettings configureDomain(Server server, String requestedDomain) {
        String customDomain = normalizeAndValidateCustomDomain(requestedDomain);
        requireCloudflareConfigured();

        try (CustomDomainLockRegistry.LockHold hold = lockRegistry.acquire(server.getId(), customDomain)) {
            return configureDomainLocked(server, customDomain);
        }
    }

    private DomainSettings configureDomainLocked(Server staleServer, String customDomain) {
        Server server = serverCustomDomainRepository.findById(staleServer.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Server not found"));
        String currentDomain = server.getCustomDomainOverride();
        String currentCloudflareId = server.getCustomDomainCloudflareId();
        boolean sameDomain = currentDomain != null && currentDomain.equalsIgnoreCase(customDomain);

        if (serverCustomDomainRepository.isCustomDomainOwnedByAnotherServer(customDomain, server.getId())) {
            throw new ConflictException(DOMAIN_UNAVAILABLE_MESSAGE);
        }

        if (sameDomain && server.getCustomDomainStatus() == CustomDomainStatus.ACTIVE) {
            CloudflareClient.CustomHostnameResult existing = currentCloudflareId == null
                                                             ? null : cloudflareClient.getCustomHostname(currentCloudflareId);
            if (existing != null && customDomain.equalsIgnoreCase(existing.hostname())) {
                return getDomainSettings(server, null);
            }
        }

        if (!sameDomain && currentDomain != null) {
            deleteExistingHostname(currentCloudflareId, currentDomain);
        }

        CloudflareClient.CustomHostnameResult collision = cloudflareClient.findCustomHostnameByName(customDomain);
        if (collision != null) {
            cloudflareClient.deleteCustomHostname(collision.id());
        }

        CloudflareClient.CustomHostnameResult cfResult = cloudflareClient.createCustomHostname(customDomain);
        if (cfResult == null) {
            throw new ExternalServiceException("Failed to provision the custom domain. Please try again.");
        }

        CustomDomainStatusMapper.Resolution resolution = statusMapper.resolve(cfResult);
        DomainSettings.DomainStatus status = buildStatus(customDomain, resolution);

        try {
            stateWriter.persist(server.getId(), customDomain, statusMapper.toEnum(resolution.status()),
                cfResult.id(), resolution.error());
        } catch (DuplicateKeyException duplicateKeyException) {
            cloudflareClient.deleteCustomHostname(cfResult.id());
            throw new ConflictException(DOMAIN_UNAVAILABLE_MESSAGE);
        }

        stateWriter.evict(currentDomain);
        settingsDocumentService.deleteState(server, SETTINGS_TYPE_DOMAIN);

        return buildResponse(server, customDomain, status, false);
    }

    public DomainSettings verifyDomain(Server server, String requestedDomain) {
        String domain = normalizeAndValidateCustomDomain(requestedDomain);
        requireCloudflareConfigured();
        String configuredDomain = server.getCustomDomainOverride();
        if (configuredDomain == null || configuredDomain.isEmpty()) {
            throw new ResourceNotFoundException("No domain configured");
        }
        if (!domain.equalsIgnoreCase(configuredDomain)) {
            throw new ValidationException("Domain does not match configured domain");
        }

        try (CustomDomainLockRegistry.LockHold hold = lockRegistry.acquire(server.getId(), configuredDomain)) {
            String cloudflareHostnameId = server.getCustomDomainCloudflareId();
            CloudflareClient.CustomHostnameResult cfResult;
            if (cloudflareHostnameId != null && !cloudflareHostnameId.isEmpty()) {
                cfResult = cloudflareClient.getCustomHostname(cloudflareHostnameId);
            } else {
                cfResult = cloudflareClient.findCustomHostnameByName(domain);
                cloudflareHostnameId = cfResult == null ? null : cfResult.id();
            }

            if (cfResult == null) {
                log.warn("Custom hostname not found in Cloudflare for domain: {}", domain);
            }

            CustomDomainStatusMapper.Resolution resolution = statusMapper.resolve(cfResult);
            DomainSettings.DomainStatus status = buildStatus(domain, resolution);

            boolean updated = stateWriter.reconcileStatus(server.getId(), domain,
                statusMapper.toEnum(resolution.status()), cloudflareHostnameId, resolution.error());
            if (!updated) {
                throw new ResourceNotFoundException("No domain configured");
            }

            return buildResponse(server, domain, status, false);
        }
    }

    public void removeDomain(Server server) {
        String customDomain = server.getCustomDomainOverride();
        String[] lockKeys = customDomain == null
                            ? new String[] {server.getId()}
                            : new String[] {server.getId(), customDomain};
        try (CustomDomainLockRegistry.LockHold hold = lockRegistry.acquire(lockKeys)) {
            deleteExistingHostname(server.getCustomDomainCloudflareId(), customDomain);
            stateWriter.clear(server.getId(), customDomain);
            settingsDocumentService.deleteState(server, SETTINGS_TYPE_DOMAIN);
        }
    }

    private void requireCloudflareConfigured() {
        if (!cloudflareConfiguration.isConfigured()) {
            throw new ServiceUnavailableException("Custom domains are not available on this deployment.");
        }
    }

    private void deleteExistingHostname(String cloudflareId, String domain) {
        String hostnameId = cloudflareId;
        if (hostnameId == null && domain != null && !domain.isEmpty()) {
            try {
                CloudflareClient.CustomHostnameResult existing = cloudflareClient.findCustomHostnameByName(domain);
                hostnameId = existing == null ? null : existing.id();
            } catch (ExternalServiceException exception) {
                log.warn("Could not look up Cloudflare custom hostname for domain {}; leaving it for orphan collection",
                    domain, exception);
                return;
            }
        }
        if (hostnameId == null) {
            return;
        }
        if (!cloudflareClient.deleteCustomHostname(hostnameId)) {
            log.warn("Failed to delete Cloudflare custom hostname for domain: {}", domain);
        }
    }

    private DomainSettings.DomainStatus buildStatus(String domain, CustomDomainStatusMapper.Resolution resolution) {
        return DomainSettings.DomainStatus.builder()
            .domain(domain)
            .status(resolution.status())
            .cnameConfigured(resolution.cnameConfigured())
            .sslStatus(resolution.sslStatus())
            .lastChecked(Instant.now().toString())
            .error(resolution.error())
            .build();
    }

    private DomainSettings.DomainStatus statusFromServer(Server server) {
        CustomDomainStatus stored = server.getCustomDomainStatus();
        String status = stored == null ? "pending" : stored.name().toLowerCase(Locale.ROOT);
        boolean active = stored == CustomDomainStatus.ACTIVE;
        String sslStatus = active ? "active" : (stored == CustomDomainStatus.ERROR ? "error" : "pending");
        String lastChecked = server.getCustomDomainLastChecked() == null
                             ? null : server.getCustomDomainLastChecked().toInstant().toString();
        return DomainSettings.DomainStatus.builder()
            .domain(server.getCustomDomainOverride())
            .status(status)
            .cnameConfigured(active)
            .sslStatus(sslStatus)
            .lastChecked(lastChecked)
            .error(server.getCustomDomainError())
            .build();
    }

    private DomainSettings buildResponse(Server server, String customDomain,
                                         DomainSettings.DomainStatus status, boolean accessingFromCustomDomain) {
        return DomainSettings.builder()
            .customDomain(customDomain)
            .status(status)
            .accessingFromCustomDomain(accessingFromCustomDomain)
            .modlSubdomainUrl("https://" + server.getCustomDomain() + ".modl.gg")
            .canManageCustomDomain(customDomainAccessService.canManageCustomDomain(server))
            .build();
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
        for (String suffix : reservedSuffixes) {
            if (domain.equals(suffix) || domain.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }
}
