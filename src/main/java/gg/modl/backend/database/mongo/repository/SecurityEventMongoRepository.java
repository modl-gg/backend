package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.admin.data.SecurityEvent;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.SecurityEventFields;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

@Repository
public class SecurityEventMongoRepository extends AbstractGlobalMongoRepository<SecurityEvent> {
    public static final String COLLECTION_NAME = "security_events";

    public SecurityEventMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(SecurityEvent.class, COLLECTION_NAME, tenantMongoAccess);
    }

    public List<SecurityEvent> findSecurityEvents(
            String type,
            String severity,
            String source,
            String search,
            Date startDate,
            Date endDate,
            int skip,
            int limit
    ) {
        Query query = buildEventsQuery(type, severity, source, search, startDate, endDate);
        query.with(Sort.by(Sort.Direction.DESC, SecurityEventFields.TIMESTAMP));
        query.skip(skip).limit(limit);
        return find(query);
    }

    public long countSecurityEvents(
            String type,
            String severity,
            String source,
            String search,
            Date startDate,
            Date endDate
    ) {
        return count(buildEventsQuery(type, severity, source, search, startDate, endDate));
    }

    public long countBySeveritySince(String severity, Date startDate) {
        return count(Query.query(new Criteria().andOperator(
                MongoQueries.where(SecurityEventFields.SEVERITY).is(severity),
                MongoQueries.where(SecurityEventFields.TIMESTAMP).gte(startDate)
        )));
    }

    public long countSince(Date startDate) {
        return count(Query.query(MongoQueries.where(SecurityEventFields.TIMESTAMP).gte(startDate)));
    }

    private Query buildEventsQuery(
            String type,
            String severity,
            String source,
            String search,
            Date startDate,
            Date endDate
    ) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (hasText(type)) {
            criteriaList.add(MongoQueries.where(SecurityEventFields.TYPE).is(type));
        }
        if (hasText(severity)) {
            criteriaList.add(MongoQueries.where(SecurityEventFields.SEVERITY).is(severity));
        }
        if (hasText(source)) {
            criteriaList.add(MongoQueries.where(SecurityEventFields.SOURCE).is(source));
        }
        if (hasText(search)) {
            criteriaList.add(MongoQueries.where(SecurityEventFields.DESCRIPTION).regex(Pattern.quote(search), "i"));
        }
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = MongoQueries.where(SecurityEventFields.TIMESTAMP);
            if (startDate != null) {
                dateCriteria = dateCriteria.gte(startDate);
            }
            if (endDate != null) {
                dateCriteria = dateCriteria.lte(endDate);
            }
            criteriaList.add(dateCriteria);
        }
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }
        return query;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

