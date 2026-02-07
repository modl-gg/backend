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

    @Value("${modl.domain}")
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

        CloudflareClient.CustomHostnameResult cfResult = cloudflareClient.findCustomHostnameByName(customDomain);

        if (cfResult != null) {
            boolean isActive = "active".equalsIgnoreCase(cfResult.status());
            boolean sslActive = cfResult.ssl() != null && "active".equalsIgnoreCase(cfResult.ssl().status());

            return new DomainStatusResponse(
                    customDomain,
                    isActive ? "active" : "pending",
                    isActive,
                    sslActive,
                    baseDomain,
                    isActive ? "Domain is active" : "Domain is pending verification. Please configure your CNAME."
            );
        }

        return new DomainStatusResponse(
                customDomain,
                "pending",
                false,
                false,
                baseDomain,
                "Custom hostname not found. Please configure your domain."
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

        CloudflareClient.CustomHostnameResult existingHostname = cloudflareClient.findCustomHostnameByName(domain);
        if (existingHostname != null) {
            cloudflareClient.deleteCustomHostname(existingHostname.id());
        }

        CloudflareClient.CustomHostnameResult cfResult = cloudflareClient.createCustomHostname(domain);

        if (cfResult == null) {
            return new DomainStatusResponse(
                    domain,
                    "error",
                    false,
                    false,
                    baseDomain,
                    "Failed to create custom hostname in Cloudflare"
            );
        }

        return new DomainStatusResponse(
                domain,
                "pending",
                false,
                false,
                baseDomain,
                "Domain added. Please configure your CNAME to point to " + baseDomain
        );
    }

    public DomainStatusResponse verifyDomain(Server server, String domain) {
        CloudflareClient.CustomHostnameResult cfResult = cloudflareClient.findCustomHostnameByName(domain);

        if (cfResult == null) {
            return new DomainStatusResponse(
                    domain,
                    "error",
                    false,
                    false,
                    baseDomain,
                    "Custom hostname not found in Cloudflare. Please reconfigure your domain."
            );
        }

        boolean isActive = "active".equalsIgnoreCase(cfResult.status());
        boolean sslActive = cfResult.ssl() != null && "active".equalsIgnoreCase(cfResult.ssl().status());

        String message;
        if (isActive && sslActive) {
            message = "Domain verified successfully with active SSL";
        } else if (isActive) {
            message = "Domain verified. SSL certificate is being provisioned.";
        } else {
            message = "Domain verification pending. Please ensure your CNAME is configured correctly.";
        }

        return new DomainStatusResponse(
                domain,
                isActive ? "active" : "pending",
                isActive,
                sslActive,
                baseDomain,
                message
        );
    }

    public boolean deleteDomain(Server server, String domain) {
        CloudflareClient.CustomHostnameResult cfResult = cloudflareClient.findCustomHostnameByName(domain);

        if (cfResult == null) {
            log.warn("Custom hostname not found in Cloudflare for domain: {}", domain);
            return true;
        }

        boolean deleted = cloudflareClient.deleteCustomHostname(cfResult.id());
        if (!deleted) {
            log.warn("Failed to delete Cloudflare custom hostname for domain: {}", domain);
        }

        return deleted;
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
