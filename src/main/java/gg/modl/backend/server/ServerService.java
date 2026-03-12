package gg.modl.backend.server;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import gg.modl.backend.server.service.ServerProvisioningService;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ServerService {
    private final ServerMongoRepository serverRepository;
    private final ServerProvisioningService provisioningService;
    private final Set<String> appDomains;
    public static final String SERVER_DATABASE_PREFIX = "server_";

    public ServerService(
        ServerMongoRepository serverRepository,
        ServerProvisioningService provisioningService,
        @Value("${modl.cors.app-domains:modl.gg,modl.top}") String appDomainsConfig
    ) {
        this.serverRepository = serverRepository;
        this.provisioningService = provisioningService;
        this.appDomains = Arrays.stream(appDomainsConfig.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());
    }

    @Async
    public void createServer(@NotNull Server server) {
        serverRepository.saveEntity(server);
    }

    public void createServer(@NotNull String serverName, @NotNull String customDomain, @NotNull String adminEmail) {
        createServer(serverName, customDomain, adminEmail, null, ServerPlan.FREE);
    }

    public Server createServer(@NotNull String serverName, @NotNull String customDomain, @NotNull String adminEmail,
                               @Nullable String emailVerificationToken, @NotNull ServerPlan plan) {
        Date now = new Date();
        String databaseName = generateDatabaseName(customDomain);

        Server server = new Server(serverName, customDomain, databaseName, adminEmail, false, plan);
        server.setProvisioningStatus(ProvisioningStatus.PENDING);
        server.setSubscriptionStatus(SubscriptionStatus.INACTIVE);
        server.setCreatedAt(now);
        server.setUpdatedAt(now);

        if (emailVerificationToken != null) {
            server.setEmailVerificationToken(emailVerificationToken);
        }

        return serverRepository.saveEntity(server);
    }

    public String generateDatabaseName(@NotNull String subdomain) {
        return SERVER_DATABASE_PREFIX + subdomain;
    }

    @Nullable
    public Server getServerFromDomain(@NotNull String domain) {
        String subdomain = extractSubdomain(domain);

        if (subdomain != null) {
            return serverRepository.findByCustomDomain(subdomain).orElse(null);
        }

        return serverRepository.findByActiveCustomDomainOverride(domain).orElse(null);
    }

    @Nullable
    private String extractSubdomain(@NotNull String domain) {
        for (String appDomain : appDomains) {
            String suffix = "." + appDomain;
            if (domain.endsWith(suffix)) {
                String subdomain = domain.substring(0, domain.length() - suffix.length());
                if (!subdomain.isBlank() && !subdomain.contains(".")) {
                    return subdomain;
                }
            }
        }
        return null;
    }

    /**
     * Returns the matching app domain (e.g. "modl.gg") if the given domain is a subdomain of one,
     * or null if it's a custom domain.
     */
    @Nullable
    public String getAppDomain(@NotNull String domain) {
        for (String appDomain : appDomains) {
            String suffix = "." + appDomain;
            if (domain.endsWith(suffix)) {
                String subdomain = domain.substring(0, domain.length() - suffix.length());
                if (!subdomain.isBlank() && !subdomain.contains(".")) {
                    return appDomain;
                }
            }
        }
        return null;
    }

    public ServerExistResult doesServerExist(@NotNull String email, @NotNull String serverName, @NotNull String subdomain) {
        Server found = serverRepository.findMatchingIdentity(email, serverName, subdomain).orElse(null);
        if (found == null) {
            return new ServerExistResult(false, false, false);
        }

        boolean emailMatch = false, nameMatch = false, domainMatch = false;

        if (found.getAdminEmail().equals(email)) {
            emailMatch = true;
        }

        if (found.getServerName().equals(serverName)) {
            nameMatch = true;
        }

        if (found.getCustomDomain().equals(subdomain)) {
            domainMatch = true;
        }

        return new ServerExistResult(emailMatch, nameMatch, domainMatch);
    }

    @Nullable
    public Server getServerByDatabaseName(@NotNull String databaseName) {
        return serverRepository.findByDatabaseName(databaseName).orElse(null);
    }

    @Nullable
    public Server getServerByApiKey(@NotNull String apiKey) {
        return serverRepository.findByApiKey(apiKey).orElse(null);
    }

    @Nullable
    public Server getServerByEmailVerificationToken(@NotNull String token) {
        return serverRepository.findByEmailVerificationToken(token).orElse(null);
    }

    @Nullable
    public Server verifyEmailToken(@NotNull String token) {
        Server server = serverRepository.findByEmailVerificationToken(token).orElse(null);

        if (server == null) {
            return null;
        }

        server.setEmailVerified(true);
        server.setEmailVerificationToken(null);
        server.setProvisioningStatus(ProvisioningStatus.COMPLETED);
        server.setUpdatedAt(new Date());
        Server saved = serverRepository.saveEntity(server);

        // Seed default data for the new server
        provisioningService.provision(saved);

        return saved;
    }

    @Nullable
    public Server getServerByAutoLoginToken(@NotNull String token) {
        return serverRepository.findByProvisioningSignInToken(token).orElse(null);
    }

    public Server setAutoLoginToken(@NotNull Server server, @NotNull String token, @NotNull Date expiresAt) {
        server.setProvisioningSignInToken(token);
        server.setProvisioningSignInTokenExpiresAt(expiresAt);
        server.setUpdatedAt(new Date());
        return serverRepository.saveEntity(server);
    }

    public Server clearAutoLoginToken(@NotNull Server server) {
        server.setProvisioningSignInToken(null);
        server.setProvisioningSignInTokenExpiresAt(null);
        server.setUpdatedAt(new Date());
        return serverRepository.saveEntity(server);
    }

    @Nullable
    public Server getServerByCliSetupToken(@NotNull String token) {
        return serverRepository.findByCliSetupToken(token).orElse(null);
    }

    public Server setCliSetupToken(@NotNull Server server, @NotNull String token) {
        server.setCliSetupToken(token);
        server.setUpdatedAt(new Date());
        return serverRepository.saveEntity(server);
    }

    public Server clearCliSetupToken(@NotNull Server server) {
        server.setCliSetupToken(null);
        server.setUpdatedAt(new Date());
        return serverRepository.saveEntity(server);
    }

    public record ServerExistResult(boolean emailMatch, boolean nameMatch, boolean domainMatch) {}
}
