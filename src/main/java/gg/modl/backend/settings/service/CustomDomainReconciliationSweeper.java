package gg.modl.backend.settings.service;

import gg.modl.backend.cloudflare.config.CloudflareConfiguration;
import gg.modl.backend.cloudflare.external.CloudflareClient;
import gg.modl.backend.database.mongo.repository.ServerCustomDomainRepository;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.server.data.CustomDomainStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.config.CustomDomainReconciliationProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomDomainReconciliationSweeper {
    private final CustomDomainReconciliationProperties properties;
    private final CloudflareConfiguration cloudflareConfiguration;
    private final CloudflareClient cloudflareClient;
    private final ServerCustomDomainRepository serverCustomDomainRepository;
    private final CustomDomainStatusMapper statusMapper;
    private final CustomDomainStateWriter stateWriter;
    private final CustomDomainLockRegistry lockRegistry;

    @Scheduled(fixedDelayString = "${modl.custom-domain.reconciliation.interval-ms:21600000}")
    public void runScheduledReconciliation() {
        runReconciliationOnce();
    }

    public void runReconciliationOnce() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!cloudflareConfiguration.isConfigured()) {
            log.debug("Skipping custom domain reconciliation; Cloudflare is not configured");
            return;
        }

        List<Server> servers = serverCustomDomainRepository.findAllWithCustomDomainOverride();
        int reconciled = 0;
        Set<String> claimedDomains = new HashSet<>();
        for (Server server : servers) {
            String domain = server.getCustomDomainOverride();
            if (domain == null || domain.isEmpty()) {
                continue;
            }
            claimedDomains.add(domain.toLowerCase(Locale.ROOT));
            if (reconcileServer(server, domain)) {
                reconciled++;
            }
        }
        log.info("Custom domain reconciliation scanned={} updated={}", servers.size(), reconciled);

        if (properties.isOrphanGarbageCollection()) {
            garbageCollectOrphans(claimedDomains);
        }
    }

    private boolean reconcileServer(Server staleServer, String domain) {
        try (CustomDomainLockRegistry.LockHold hold = lockRegistry.acquire(staleServer.getId(), domain)) {
            Server server = serverCustomDomainRepository.findById(staleServer.getId()).orElse(null);
            if (server == null || !domain.equalsIgnoreCase(server.getCustomDomainOverride())) {
                return false;
            }

            String cloudflareId = server.getCustomDomainCloudflareId();
            CloudflareClient.CustomHostnameResult cfResult;
            try {
                cfResult = lookupHostname(cloudflareId, domain);
            } catch (ExternalServiceException exception) {
                log.warn("Skipping custom domain reconciliation for {}; Cloudflare lookup failed", domain, exception);
                return false;
            }

            CustomDomainStatusMapper.Resolution resolution = statusMapper.resolve(cfResult);
            CustomDomainStatus target = statusMapper.toEnum(resolution.status());
            String resolvedCloudflareId = cfResult == null ? cloudflareId : cfResult.id();

            if (target == server.getCustomDomainStatus()
                && Objects.equals(resolution.error(), server.getCustomDomainError())) {
                return false;
            }

            boolean updated = stateWriter.reconcileStatus(
                server.getId(), domain, target, resolvedCloudflareId, resolution.error());
            if (updated) {
                log.info("Custom domain {} reconciled {} -> {}", domain, server.getCustomDomainStatus(), target);
            }
            return updated;
        }
    }

    private CloudflareClient.CustomHostnameResult lookupHostname(String cloudflareId, String domain) {
        CloudflareClient.CustomHostnameResult cfResult = cloudflareId != null && !cloudflareId.isEmpty()
                                                         ? cloudflareClient.getCustomHostname(cloudflareId)
                                                         : null;
        return cfResult != null ? cfResult : cloudflareClient.findCustomHostnameByName(domain);
    }

    private void garbageCollectOrphans(Set<String> claimedDomains) {
        List<CloudflareClient.CustomHostnameResult> hostnames = cloudflareClient.listAllCustomHostnames();
        int deleted = 0;
        for (CloudflareClient.CustomHostnameResult hostname : hostnames) {
            if (hostname.hostname() == null || hostname.id() == null) {
                continue;
            }
            String normalized = hostname.hostname().toLowerCase(Locale.ROOT);
            if (claimedDomains.contains(normalized)) {
                continue;
            }
            if (deleteOrphan(normalized, hostname.id())) {
                deleted++;
            }
        }
        if (deleted > 0) {
            log.info("Custom domain orphan collection deleted={} of scanned={}", deleted, hostnames.size());
        }
    }

    private boolean deleteOrphan(String normalizedDomain, String hostnameId) {
        try (CustomDomainLockRegistry.LockHold hold = lockRegistry.acquire(normalizedDomain)) {
            if (serverCustomDomainRepository.isCustomDomainClaimed(normalizedDomain)) {
                return false;
            }
            if (cloudflareClient.deleteCustomHostname(hostnameId)) {
                log.info("Deleted orphaned Cloudflare custom hostname {}", normalizedDomain);
                return true;
            }
            log.warn("Failed to delete orphaned Cloudflare custom hostname {}", normalizedDomain);
            return false;
        }
    }
}
