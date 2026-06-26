package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.beta.data.BetaAudit;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.BetaAuditFields;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class BetaAuditMongoRepository extends AbstractGlobalMongoRepository<BetaAudit> {
    public BetaAuditMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(BetaAudit.class, CollectionName.BETA_AUDIT, tenantMongoAccess);
    }

    public List<BetaAudit> findRecentForServer(String serverId, int limit) {
        Query query = Query.query(Criteria.where(BetaAuditFields.SERVER_ID).is(serverId));
        query.with(Sort.by(Sort.Direction.DESC, BetaAuditFields.TIMESTAMP));
        query.limit(limit);
        return find(query);
    }
}
