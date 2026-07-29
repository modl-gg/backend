package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ServerBetaTesterRepository extends AbstractGlobalMongoRepository<Server> {

    public ServerBetaTesterRepository(TenantMongoAccess tenantMongoAccess) {
        super(Server.class, CollectionName.MODL_SERVERS, tenantMongoAccess);
    }

    public List<Server> findBetaTesters(String search, int skip, int limit) {
        Query query = buildBetaTesterQuery(search);
        query.with(Sort.by(Sort.Direction.DESC, ServerFields.BETA_TESTER_CREATED_AT));
        query.skip(skip).limit(limit);
        return find(query);
    }

    public long countBetaTesters(String search) {
        return count(buildBetaTesterQuery(search));
    }

    public List<Server> findAllBetaTesters() {
        return find(Query.query(Criteria.where(ServerFields.BETA_TESTER).is(true)));
    }

    private Query buildBetaTesterQuery(String search) {
        Criteria betaCriteria = Criteria.where(ServerFields.BETA_TESTER_CREATED_AT).exists(true);
        if (search == null || search.trim().isEmpty()) {
            return new Query(betaCriteria);
        }
        String escapedSearch = Pattern.quote(search.trim());
        return new Query(new Criteria().andOperator(
            betaCriteria,
            new Criteria().orOperator(
                Criteria.where(ServerFields.SERVER_NAME).regex(escapedSearch, "i"),
                Criteria.where(ServerFields.CUSTOM_DOMAIN).regex(escapedSearch, "i"),
                Criteria.where(ServerFields.ADMIN_EMAIL).regex(escapedSearch, "i")
            )
        ));
    }

    public Optional<Server> updateBetaState(String serverId, ServerPlan plan, SubscriptionStatus subscriptionStatus, boolean betaTester) {
        Update update = new Update()
            .set(ServerFields.PLAN, plan)
            .set(ServerFields.SUBSCRIPTION_STATUS, subscriptionStatus)
            .set(ServerFields.BETA_TESTER, betaTester)
            .set(ServerFields.UPDATED_AT, new Date());
        return Optional.ofNullable(findAndModify(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            update,
            FindAndModifyOptions.options().returnNew(true)
        ));
    }
}
