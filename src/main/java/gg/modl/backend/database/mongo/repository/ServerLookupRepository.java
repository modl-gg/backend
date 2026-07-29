package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class ServerLookupRepository extends AbstractGlobalMongoRepository<Server> {
    private static final String DOMAIN_STATUS_ACTIVE = "ACTIVE";

    public ServerLookupRepository(TenantMongoAccess tenantMongoAccess) {
        super(Server.class, CollectionName.MODL_SERVERS, tenantMongoAccess);
    }

    public Optional<Server> findByCustomDomain(String customDomain) {
        return findOne(Query.query(Criteria.where(ServerFields.CUSTOM_DOMAIN).is(customDomain)));
    }

    public Optional<Server> findByActiveCustomDomainOverride(String domain) {
        Criteria criteria = new Criteria().andOperator(
            Criteria.where(ServerFields.CUSTOM_DOMAIN_OVERRIDE).is(domain),
            Criteria.where(ServerFields.CUSTOM_DOMAIN_STATUS).is(DOMAIN_STATUS_ACTIVE)
        );
        return findOne(new Query(criteria));
    }

    public Optional<Server> findMatchingIdentity(String email, String serverName, String subdomain) {
        Criteria criteria = new Criteria().orOperator(
            Criteria.where(ServerFields.ADMIN_EMAIL).is(email),
            Criteria.where(ServerFields.SERVER_NAME).is(serverName),
            Criteria.where(ServerFields.CUSTOM_DOMAIN).is(subdomain)
        );
        return findOne(new Query(criteria));
    }

    public Optional<Server> findByDatabaseName(String databaseName) {
        return findOne(Query.query(Criteria.where(ServerFields.DATABASE_NAME).is(databaseName)));
    }

    public Optional<Server> findByApiKey(String apiKey) {
        return findOne(Query.query(Criteria.where(ServerFields.API_KEY).is(apiKey)));
    }

    public boolean existsByAdminEmailExcludingId(String adminEmail, String excludedServerId) {
        Criteria criteria = Criteria.where(ServerFields.ADMIN_EMAIL)
            .regex("^" + Pattern.quote(adminEmail) + "$", "i")
            .and(ServerFields.ID).ne(excludedServerId);
        return exists(Query.query(criteria));
    }

    public Optional<Server> findByEmailVerificationToken(String token) {
        return findOne(Query.query(Criteria.where(ServerFields.EMAIL_VERIFICATION_TOKEN).is(token)));
    }

    public Optional<Server> findByProvisioningSignInToken(String token) {
        return findOne(Query.query(Criteria.where(ServerFields.PROVISIONING_SIGN_IN_TOKEN).is(token)));
    }

    public Optional<Server> findByCliSetupToken(String token) {
        return findOne(Query.query(Criteria.where(ServerFields.CLI_SETUP_TOKEN).is(token)));
    }

    public Optional<Server> findByStripeCustomerId(String customerId) {
        if (customerId == null) {
            return Optional.empty();
        }
        return findOne(Query.query(Criteria.where(ServerFields.STRIPE_CUSTOMER_ID).is(customerId)));
    }

    public Optional<Server> findByStripeSubscriptionId(String subscriptionId) {
        return findOne(Query.query(Criteria.where(ServerFields.STRIPE_SUBSCRIPTION_ID).is(subscriptionId)));
    }

    public List<Server> findCancelledWithPeriodEnd() {
        Criteria criteria = new Criteria().andOperator(
            Criteria.where(ServerFields.SUBSCRIPTION_STATUS).is(SubscriptionStatus.CANCELED),
            Criteria.where(ServerFields.CURRENT_PERIOD_END).exists(true).ne(null)
        );
        return find(new Query(criteria));
    }
}
