package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.admin.data.SecurityEvent;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.SecurityEventFields;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class SecurityEventMongoRepository extends AbstractGlobalMongoRepository<SecurityEvent> {
    public SecurityEventMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(SecurityEvent.class, CollectionName.SECURITY_EVENTS, tenantMongoAccess);
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

        if (StringUtils.hasText(type)) {
            criteriaList.add(Criteria.where(SecurityEventFields.TYPE).is(type));
        }
        if (StringUtils.hasText(severity)) {
            criteriaList.add(Criteria.where(SecurityEventFields.SEVERITY).is(severity));
        }
        if (StringUtils.hasText(source)) {
            criteriaList.add(Criteria.where(SecurityEventFields.SOURCE).is(source));
        }
        if (StringUtils.hasText(search)) {
            criteriaList.add(Criteria.where(SecurityEventFields.DESCRIPTION).regex(Pattern.quote(search), "i"));
        }
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where(SecurityEventFields.TIMESTAMP);
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
            Criteria.where(SecurityEventFields.SEVERITY).is(severity),
            Criteria.where(SecurityEventFields.TIMESTAMP).gte(startDate)
        )));
    }

    public long countSince(Date startDate) {
        return count(Query.query(Criteria.where(SecurityEventFields.TIMESTAMP).gte(startDate)));
    }
}

