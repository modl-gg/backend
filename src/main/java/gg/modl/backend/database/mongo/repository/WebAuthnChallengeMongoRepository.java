package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.auth.data.WebAuthnChallenge;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.WebAuthnChallengeFields;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class WebAuthnChallengeMongoRepository extends AbstractServerMongoRepository<WebAuthnChallenge> {
    public WebAuthnChallengeMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(WebAuthnChallenge.class, CollectionName.WEBAUTHN_CHALLENGES, tenantMongoAccess);
    }

    public Optional<WebAuthnChallenge> consumeActiveChallenge(Server server, String challengeId, Date now) {
        Query query = Query.query(new Criteria().andOperator(
            MongoQueries.where(WebAuthnChallengeFields.ID).is(challengeId),
            MongoQueries.where(WebAuthnChallengeFields.EXPIRES_AT).gt(now)
        ));
        return Optional.ofNullable(findAndRemove(server, query));
    }
}
