package gg.modl.backend.server;

import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.*;
import gg.modl.backend.server.service.ServerProvisioningService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ServerService {
    public static final String SERVER_DATABASE_PREFIX = "server_";
    private final ServerMongoRepository serverRepository;
    private final ServerProvisioningService provisioningService;
    private final Set<String> appDomains;

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
            Query query = Query.query(MongoQueries.where(ServerFields.CUSTOM_DOMAIN).is(subdomain));
            return serverRepository.findOne(query).orElse(null);
        }

        // Strictly require active custom domain status after schema cutover.
        Criteria customDomainCriteria = new Criteria().andOperator(
                MongoQueries.where(ServerFields.CUSTOM_DOMAIN_OVERRIDE).is(domain),
                MongoQueries.where(ServerFields.CUSTOM_DOMAIN_STATUS).is(CustomDomainStatus.ACTIVE.name())
        );
        return serverRepository.findOne(new Query(customDomainCriteria)).orElse(null);
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

    public ServerExistResult doesServerExist(@NotNull String email, @NotNull String serverName, @NotNull String subdomain) {
        Criteria emailCriteria = MongoQueries.where(ServerFields.ADMIN_EMAIL).is(email);
        Criteria nameCriteria = MongoQueries.where(ServerFields.SERVER_NAME).is(serverName);
        Criteria domainCriteria = MongoQueries.where(ServerFields.CUSTOM_DOMAIN).is(subdomain);

        Server found = serverRepository.findOne(new Query(new Criteria().orOperator(emailCriteria, nameCriteria, domainCriteria))).orElse(null);
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
        Query query = Query.query(MongoQueries.where(ServerFields.DATABASE_NAME).is(databaseName));
        return serverRepository.findOne(query).orElse(null);
    }

    @Nullable
    public Server getServerByApiKey(@NotNull String apiKey) {
        Query query = Query.query(MongoQueries.where(ServerFields.API_KEY).is(apiKey));
        return serverRepository.findOne(query).orElse(null);
    }

    @Nullable
    public Server getServerByEmailVerificationToken(@NotNull String token) {
        Query query = Query.query(MongoQueries.where(ServerFields.EMAIL_VERIFICATION_TOKEN).is(token));
        return serverRepository.findOne(query).orElse(null);
    }

    @Nullable
    public Server verifyEmailToken(@NotNull String token) {
        Query query = Query.query(MongoQueries.where(ServerFields.EMAIL_VERIFICATION_TOKEN).is(token));
        Server server = serverRepository.findOne(query).orElse(null);

        if (server == null) {
            return null;
        }

        Server original = serverRepository.snapshot(server);
        server.setEmailVerified(true);
        server.setEmailVerificationToken(null);
        server.setProvisioningStatus(ProvisioningStatus.COMPLETED);
        server.setUpdatedAt(new Date());
        Server saved = serverRepository.saveChanges(original, server);

        // Seed default data for the new server
        provisioningService.provision(saved);

        return saved;
    }

    @Nullable
    public Server getServerByAutoLoginToken(@NotNull String token) {
        Query query = Query.query(MongoQueries.where(ServerFields.PROVISIONING_SIGN_IN_TOKEN).is(token));
        return serverRepository.findOne(query).orElse(null);
    }

    public Server setAutoLoginToken(@NotNull Server server, @NotNull String token, @NotNull Date expiresAt) {
        Server original = serverRepository.snapshot(server);
        server.setProvisioningSignInToken(token);
        server.setProvisioningSignInTokenExpiresAt(expiresAt);
        server.setUpdatedAt(new Date());
        return serverRepository.saveChanges(original, server);
    }

    public Server clearAutoLoginToken(@NotNull Server server) {
        Server original = serverRepository.snapshot(server);
        server.setProvisioningSignInToken(null);
        server.setProvisioningSignInTokenExpiresAt(null);
        server.setUpdatedAt(new Date());
        return serverRepository.saveChanges(original, server);
    }

    public record ServerExistResult(boolean emailMatch, boolean nameMatch, boolean domainMatch) {}
}
