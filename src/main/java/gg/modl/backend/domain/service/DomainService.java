package gg.modl.backend.domain.service;

import gg.modl.backend.domain.dto.response.DomainInstructionsResponse;
import gg.modl.backend.domain.dto.response.DomainStatusResponse;
import gg.modl.backend.domain.external.CloudflareClient;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainService {
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.[A-Za-z0-9-]{1,63})*\\.[A-Za-z]{2,}$"
    );

    private final CloudflareClient cloudflareClient;

    @Value("${modl.domain:modl.gg}")
    private String baseDomain;

    public DomainStatusResponse getDomainConfig(Server server) {
        String customDomain = server.getCustomDomain();

        if (customDomain == null || customDomain.isBlank()) {
            return new DomainStatusResponse(
                    null,
                    "not_configured",
                    false,
                    false,
                    baseDomain,
                    "No custom domain configured"
            );
        }

        boolean dnsConfigured = cloudflareClient.verifyDnsRecord(customDomain);

        return new DomainStatusResponse(
                customDomain,
                dnsConfigured ? "active" : "pending",
                dnsConfigured,
                dnsConfigured,
                baseDomain,
                dnsConfigured ? "Domain is active" : "DNS record not found. Please configure your CNAME."
        );
    }

    public DomainStatusResponse addDomain(Server server, String domain) {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("Domain cannot be empty");
        }

        domain = domain.toLowerCase().trim();

        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new IllegalArgumentException("Invalid domain format");
        }

        if (domain.endsWith("." + baseDomain) || domain.equals(baseDomain)) {
            throw new IllegalArgumentException("Cannot use a subdomain of " + baseDomain);
        }

        boolean success = cloudflareClient.addDnsRecord(domain, baseDomain);

        return new DomainStatusResponse(
                domain,
                success ? "pending" : "error",
                false,
                false,
                baseDomain,
                success ? "Domain added. DNS propagation may take up to 24 hours." : "Failed to add domain"
        );
    }

    public DomainStatusResponse verifyDomain(Server server, String domain) {
        boolean verified = cloudflareClient.verifyDnsRecord(domain);

        return new DomainStatusResponse(
                domain,
                verified ? "active" : "pending",
                verified,
                verified,
                baseDomain,
                verified ? "Domain verified successfully" : "DNS record not yet propagated"
        );
    }

    public boolean deleteDomain(Server server, String domain) {
        return cloudflareClient.deleteDnsRecord(domain);
    }

    public DomainInstructionsResponse getInstructions() {
        return new DomainInstructionsResponse(
                baseDomain,
                List.of(
                        new DomainInstructionsResponse.InstructionStep(
                                1,
                                "Go to your DNS provider",
                                "Log in to your domain registrar or DNS provider's control panel"
                        ),
                        new DomainInstructionsResponse.InstructionStep(
                                2,
                                "Add a CNAME record",
                                "Create a new CNAME record pointing to: " + baseDomain
                        ),
                        new DomainInstructionsResponse.InstructionStep(
                                3,
                                "Wait for propagation",
                                "DNS changes can take up to 24 hours to propagate worldwide"
                        ),
                        new DomainInstructionsResponse.InstructionStep(
                                4,
                                "Verify your domain",
                                "Click 'Verify' to check if your domain is correctly configured"
                        )
                )
        );
    }
}
