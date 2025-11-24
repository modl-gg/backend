package gg.modl.backend.server;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
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
                ProvisioningStatus.PENDING,
                ServerPlan.FREE,
                SubscriptionStatus.INACTIVE,
                now,
                now
        ));
    }

    public String generateDatabaseName(@NotNull String subdomain) {
        return SERVER_DATABASE_PREFIX + subdomain;
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
