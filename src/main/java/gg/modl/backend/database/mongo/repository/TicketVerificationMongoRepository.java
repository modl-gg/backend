package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.TicketVerificationFields;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.TicketVerification;
import java.util.Date;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class TicketVerificationMongoRepository extends AbstractServerMongoRepository<TicketVerification> {
    public TicketVerificationMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(TicketVerification.class, CollectionName.TICKET_VERIFICATIONS, tenantMongoAccess);
    }

    public void replaceCodeVerification(Server server, TicketVerification verification) {
        remove(server, activeCodeQuery(verification.getTicketId()));
        saveEntity(server, verification);
    }

    private Query activeCodeQuery(String ticketId) {
        return Query.query(new Criteria().andOperator(
            Criteria.where(TicketVerificationFields.TICKET_ID).is(ticketId),
            Criteria.where(TicketVerificationFields.TOKEN).exists(false)
        ));
    }

    public Optional<TicketVerification> consumeMatchingCode(Server server, String ticketId, String codeHash, Date now) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(TicketVerificationFields.TICKET_ID).is(ticketId),
            Criteria.where(TicketVerificationFields.CODE_HASH).is(codeHash),
            Criteria.where(TicketVerificationFields.TOKEN).exists(false),
            Criteria.where(TicketVerificationFields.EXPIRES_AT).gte(now)
        ));
        return Optional.ofNullable(findAndRemove(server, query));
    }

    public boolean existsActiveToken(Server server, String ticketId, String token, Date now) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(TicketVerificationFields.TICKET_ID).is(ticketId),
            Criteria.where(TicketVerificationFields.TOKEN).is(token),
            Criteria.where(TicketVerificationFields.EXPIRES_AT).gte(now)
        ));
        return exists(server, query);
    }
}
