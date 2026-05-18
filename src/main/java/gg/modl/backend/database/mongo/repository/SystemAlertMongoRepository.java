package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.alert.data.SystemAlert;
import gg.modl.backend.alert.data.SystemAlertAudience;
import gg.modl.backend.alert.data.SystemAlertSeverity;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.SystemAlertFields;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class SystemAlertMongoRepository extends AbstractGlobalMongoRepository<SystemAlert> {
    public SystemAlertMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(SystemAlert.class, CollectionName.SYSTEM_ALERTS, tenantMongoAccess);
    }

    public List<SystemAlert> findAllOrdered() {
        return find(new Query().with(Sort.by(Sort.Direction.DESC, SystemAlertFields.CREATED_AT)));
    }

    public List<SystemAlert> findVisible(Date now) {
        Query query = Query.query(Criteria.where(SystemAlertFields.EXPIRES_AT).gt(now))
            .with(Sort.by(Sort.Direction.DESC, SystemAlertFields.CREATED_AT));
        return find(query);
    }

    public Optional<SystemAlert> findByAlertId(String id) {
        return findById(id);
    }

    public Optional<SystemAlert> updateAlert(
        String id,
        String message,
        SystemAlertSeverity severity,
        SystemAlertAudience audience,
        Date expiresAt,
        Date updatedAt,
        String updatedBy
    ) {
        Update update = new Update()
            .set(SystemAlertFields.UPDATED_AT, updatedAt)
            .set(SystemAlertFields.UPDATED_BY, updatedBy);
        if (message != null) {
            update.set(SystemAlertFields.MESSAGE, message);
        }
        if (severity != null) {
            update.set(SystemAlertFields.SEVERITY, severity);
        }
        if (audience != null) {
            update.set(SystemAlertFields.AUDIENCE, audience);
        }
        if (expiresAt != null) {
            update.set(SystemAlertFields.EXPIRES_AT, expiresAt);
        }

        SystemAlert updated = findAndModify(
            Query.query(Criteria.where(SystemAlertFields.ID).is(id)),
            update,
            FindAndModifyOptions.options().returnNew(true)
        );
        return Optional.ofNullable(updated);
    }
}
