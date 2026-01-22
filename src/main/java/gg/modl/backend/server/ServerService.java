package gg.modl.backend.server;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
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
    private final DynamicMongoTemplateProvider mongoProvider;
    private final Set<String> appDomains;

    public ServerService(
            DynamicMongoTemplateProvider mongoProvider,
            @Value("${modl.cors.app-domains:modl.gg}") String appDomainsConfig
    ) {
        this.mongoProvider = mongoProvider;
        this.appDomains = Arrays.stream(appDomainsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    @Async
    public void createServer(@NotNull Server server) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();

        db.save(server, CollectionName.MODL_SERVERS);
    }

    public void createServer(@NotNull String serverName, @NotNull String customDomain, @NotNull String adminEmail) {
        createServer(serverName, customDomain, adminEmail, null, ServerPlan.free);
    }

    public Server createServer(@NotNull String serverName, @NotNull String customDomain, @NotNull String adminEmail,
                             @Nullable String emailVerificationToken, @NotNull ServerPlan plan) {
        Date now = new Date();
        String databaseName = generateDatabaseName(customDomain);

        Server server = new Server(
                serverName,
                customDomain,
                databaseName,
                adminEmail,
                false,
                ProvisioningStatus.pending,
                plan,
                SubscriptionStatus.inactive,
                now,
                now
        );

        if (emailVerificationToken != null) {
            server.setEmailVerificationToken(emailVerificationToken);
        }

        MongoTemplate db = mongoProvider.getGlobalDatabase();
        return db.save(server, CollectionName.MODL_SERVERS);
    }

    public String generateDatabaseName(@NotNull String subdomain) {
        return SERVER_DATABASE_PREFIX + subdomain;
    }

    @Nullable
    public Server getServerFromDomain(@NotNull String domain) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();

        String subdomain = extractSubdomain(domain);

        if (subdomain != null) {
            Query query = new Query(Criteria.where(ServerField.SUBDOMAIN).is(subdomain));
            return db.findOne(query, Server.class, CollectionName.MODL_SERVERS);
        }

        Criteria customDomainCriteria = new Criteria().andOperator(
                Criteria.where(ServerField.CUSTOM_DOMAIN).is(domain),
                Criteria.where(ServerField.CUSTOM_DOMAIN_STATUS).is(CustomDomainStatus.active.name().toLowerCase())
        );
        return db.findOne(new Query(customDomainCriteria), Server.class, CollectionName.MODL_SERVERS);
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
        MongoTemplate db = mongoProvider.getGlobalDatabase();

        Criteria emailCriteria = Criteria.where(ServerField.ADMIN_EMAIL).is(email);
        Criteria nameCriteria = Criteria.where(ServerField.SERVER_NAME).is(serverName);
        Criteria domainCriteria = Criteria.where(ServerField.SUBDOMAIN).is(subdomain);

        Server found = db.findOne(new Query(new Criteria().orOperator(emailCriteria, nameCriteria, domainCriteria)), Server.class, CollectionName.MODL_SERVERS);
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
    public Server getServerByApiKey(@NotNull String apiKey) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();
        Query query = new Query(Criteria.where(ServerField.API_KEY).is(apiKey));
        return db.findOne(query, Server.class, CollectionName.MODL_SERVERS);
    }

    @Nullable
    public Server getServerByEmailVerificationToken(@NotNull String token) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();
        Query query = new Query(Criteria.where("emailVerificationToken").is(token));
        return db.findOne(query, Server.class, CollectionName.MODL_SERVERS);
    }

    @Nullable
    public Server verifyEmailToken(@NotNull String token) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();
        Query query = new Query(Criteria.where("emailVerificationToken").is(token));
        Server server = db.findOne(query, Server.class, CollectionName.MODL_SERVERS);

        if (server == null) {
            return null;
        }

        server.setEmailVerified(true);
        server.setEmailVerificationToken(null);
        server.setUpdatedAt(new Date());
        return db.save(server, CollectionName.MODL_SERVERS);
    }

    @Nullable
    public Server getServerByAutoLoginToken(@NotNull String token) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();
        Query query = new Query(Criteria.where("provisioningSignInToken").is(token));
        return db.findOne(query, Server.class, CollectionName.MODL_SERVERS);
    }

    public Server setAutoLoginToken(@NotNull Server server, @NotNull String token, @NotNull Date expiresAt) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();
        server.setProvisioningSignInToken(token);
        server.setProvisioningSignInTokenExpiresAt(expiresAt);
        server.setUpdatedAt(new Date());
        return db.save(server, CollectionName.MODL_SERVERS);
    }

    public Server clearAutoLoginToken(@NotNull Server server) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();
        server.setProvisioningSignInToken(null);
        server.setProvisioningSignInTokenExpiresAt(null);
        server.setUpdatedAt(new Date());
        return db.save(server, CollectionName.MODL_SERVERS);
    }

    public record ServerExistResult(boolean emailMatch, boolean nameMatch, boolean domainMatch) {}
}
