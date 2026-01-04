package gg.modl.backend.domain.dto.response;

public record DomainStatusResponse(
        String domain,
        String status,
        boolean dnsConfigured,
        boolean sslActive,
        String cnameTarget,
        String message
) {
}
