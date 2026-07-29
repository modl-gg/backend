package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.audit.data.AuditLog;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.AuditLogFields;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository extends AbstractServerMongoRepository<AuditLog> {
    private static final String LEVEL_MODERATION = "moderation";
    private static final String LEVEL_INFO = "info";

    public AuditLogRepository(TenantMongoAccess tenantMongoAccess) {
        super(AuditLog.class, CollectionName.LOGS, tenantMongoAccess);
    }

    public void saveAuditLog(Server server, AuditLog auditLog) {
        saveEntity(server, auditLog);
    }

    public List<AuditLog> findPunishmentLogs(Server server, Date startDate, int limit, boolean canRollbackOnly) {
        Criteria criteria = Criteria.where(AuditLogFields.CREATED).gte(startDate)
            .orOperator(
                Criteria.where(AuditLogFields.LEVEL).is(LEVEL_MODERATION),
                Criteria.where(AuditLogFields.DESCRIPTION).regex(Pattern.compile("ban|mute|kick|warn", Pattern.CASE_INSENSITIVE))
            );

        if (canRollbackOnly) {
            criteria = criteria.and(AuditLogFields.METADATA_CAN_ROLLBACK).ne(false);
        }

        Query query = Query.query(criteria)
            .with(Sort.by(Sort.Direction.DESC, AuditLogFields.CREATED))
            .limit(limit);

        return find(server, query);
    }

    public long countEvidenceUploads(Server server, String username, Date startDate) {
        Criteria baseCriteria = Criteria.where(AuditLogFields.SOURCE).is(username);
        if (startDate != null) {
            baseCriteria = baseCriteria.and(AuditLogFields.CREATED).gte(startDate);
        }

        Query query = Query.query(
            baseCriteria.orOperator(
                Criteria.where(AuditLogFields.DESCRIPTION).regex(Pattern.compile("evidence|upload|file", Pattern.CASE_INSENSITIVE)),
                Criteria.where(AuditLogFields.LEVEL)
                    .is(LEVEL_INFO)
                    .and(AuditLogFields.DESCRIPTION)
                    .regex(Pattern.compile("uploaded|attachment", Pattern.CASE_INSENSITIVE))
            )
        );

        return count(server, query);
    }
}
