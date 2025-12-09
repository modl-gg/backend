package gg.modl.backend.server;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.*;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class ServerService {
    public static final String SERVER_DATABASE_PREFIX = "server_";
    private final DynamicMongoTemplateProvider mongoProvider;

    @Async
    public void createServer(@NotNull Server server) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();

        db.save(server, CollectionName.MODL_SERVERS);
    }

    public void createServer(@NotNull String serverName, @NotNull String customDomain, @NotNull String adminEmail) {
        Date now = new Date();
        String databaseName = generateDatabaseName(customDomain);

        createServer(new Server(
                serverName,
                customDomain,
                databaseName,
                adminEmail,
                false,
                ProvisioningStatus.pending,
                ServerPlan.free,
                SubscriptionStatus.inactive,
                now,
                now
        ));
    }

    public String generateDatabaseName(@NotNull String subdomain) {
        return SERVER_DATABASE_PREFIX + subdomain;
    }

    @Nullable
    public Server getServerFromDomain(@NotNull String domain) {
        MongoTemplate db = mongoProvider.getGlobalDatabase();

        // domain is either subdomain or custom domain
        // scans for subdomain
        Criteria subdomainCriteria = Criteria.where(ServerField.SUBDOMAIN).is(domain);
        // scans for custom domain and makes sure custom domain is active
        Criteria customDomainCriteria = new Criteria().andOperator(Criteria.where(ServerField.CUSTOM_DOMAIN).is(domain), Criteria.where(ServerField.CUSTOM_DOMAIN_STATUS).is(CustomDomainStatus.active.name().toLowerCase()));

        Query query = new Query(new Criteria().orOperator(subdomainCriteria, customDomainCriteria));
        return db.findOne(query, Server.class, CollectionName.MODL_SERVERS);
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

    public record ServerExistResult(boolean emailMatch, boolean nameMatch, boolean domainMatch) {}
}
