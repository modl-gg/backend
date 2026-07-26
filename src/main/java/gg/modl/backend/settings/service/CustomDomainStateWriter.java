package gg.modl.backend.settings.service;

import gg.modl.backend.database.mongo.repository.ServerCustomDomainRepository;
import gg.modl.backend.infrastructure.cors.DynamicCorsConfigurationSource;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.CustomDomainStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomDomainStateWriter {
    private final ServerCustomDomainRepository serverCustomDomainRepository;
    private final ServerService serverService;
    private final DynamicCorsConfigurationSource corsConfigurationSource;

    public void persist(String serverId, String domain, CustomDomainStatus status,
                        String cloudflareHostnameId, String error) {
        serverCustomDomainRepository.updateCustomDomain(serverId, domain, status, cloudflareHostnameId, error);
        evict(domain);
    }

    public boolean reconcileStatus(String serverId, String domain, CustomDomainStatus status,
                                   String cloudflareHostnameId, String error) {
        boolean updated = serverCustomDomainRepository.updateCustomDomainStatus(
            serverId, domain, status, cloudflareHostnameId, error);
        if (updated) {
            evict(domain);
        }
        return updated;
    }

    public void clear(String serverId, String domain) {
        serverCustomDomainRepository.clearCustomDomain(serverId);
        evict(domain);
    }

    public void evict(String domain) {
        if (domain == null || domain.isBlank()) {
            return;
        }
        serverService.evictServerCache(domain);
        corsConfigurationSource.invalidateCache(domain);
    }
}
