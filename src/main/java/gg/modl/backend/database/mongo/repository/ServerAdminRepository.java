package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ServerAdminRepository extends AbstractGlobalMongoRepository<Server> {
    private static final String FILTER_ALL = "all";
    private static final String FILTER_ACTIVE = "active";
    private static final String FILTER_PENDING = "pending";
    private static final String FILTER_FAILED = "failed";
    private static final String FILTER_UNVERIFIED = "unverified";

    private static final String ORDER_ASC = "asc";
    private static final String INVALID_PLAN_SENTINEL = "__invalid_plan__";

    private static final Set<String> ADMIN_SORT_FIELDS = Set.of(
        ServerFields.SERVER_NAME,
        ServerFields.CUSTOM_DOMAIN,
        ServerFields.ADMIN_EMAIL,
        ServerFields.PLAN,
        ServerFields.CREATED_AT,
        ServerFields.UPDATED_AT,
        ServerFields.USER_COUNT,
        ServerFields.PROVISIONING_STATUS,
        ServerFields.LAST_ACTIVITY_AT
    );

    public ServerAdminRepository(TenantMongoAccess tenantMongoAccess) {
        super(Server.class, CollectionName.MODL_SERVERS, tenantMongoAccess);
    }

    public List<Server> findAdminServers(String search, String plan, String status, String sortField, String sortOrder, int skip, int limit) {
        Query query = buildAdminServerFilterQuery(search, plan, status);
        query.with(Sort.by(resolveSortDirection(sortOrder), resolveAdminSortField(sortField)));
        query.skip(skip).limit(limit);
        query.fields()
            .include(ServerFields.SERVER_NAME)
            .include(ServerFields.CUSTOM_DOMAIN)
            .include(ServerFields.ADMIN_EMAIL)
            .include(ServerFields.PLAN)
            .include(ServerFields.EMAIL_VERIFIED)
            .include(ServerFields.PROVISIONING_STATUS)
            .include(ServerFields.CREATED_AT)
            .include(ServerFields.UPDATED_AT)
            .include(ServerFields.USER_COUNT)
            .include(ServerFields.TICKET_COUNT)
            .include(ServerFields.LAST_ACTIVITY_AT);
        return find(query);
    }

    private Query buildAdminServerFilterQuery(String search, String plan, String status) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            String escapedSearch = Pattern.quote(search.trim());
            criteriaList.add(new Criteria().orOperator(
                Criteria.where(ServerFields.SERVER_NAME).regex(escapedSearch, "i"),
                Criteria.where(ServerFields.CUSTOM_DOMAIN).regex(escapedSearch, "i"),
                Criteria.where(ServerFields.ADMIN_EMAIL).regex(escapedSearch, "i")
            ));
        }

        if (plan != null && !FILTER_ALL.equals(plan)) {
            try {
                criteriaList.add(Criteria.where(ServerFields.PLAN).is(ServerPlan.valueOf(plan.trim().toUpperCase(Locale.ROOT))));
            } catch (IllegalArgumentException ignored) {
                criteriaList.add(Criteria.where(ServerFields.PLAN).is(INVALID_PLAN_SENTINEL));
            }
        }

        if (status != null && !FILTER_ALL.equals(status)) {
            switch (status) {
                case FILTER_ACTIVE -> {
                    criteriaList.add(Criteria.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.COMPLETED));
                    criteriaList.add(Criteria.where(ServerFields.EMAIL_VERIFIED).is(true));
                }
                case FILTER_PENDING -> criteriaList.add(Criteria.where(ServerFields.PROVISIONING_STATUS)
                    .in(ProvisioningStatus.PENDING, ProvisioningStatus.IN_PROGRESS));
                case FILTER_FAILED -> criteriaList.add(Criteria.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.FAILED));
                case FILTER_UNVERIFIED -> criteriaList.add(Criteria.where(ServerFields.EMAIL_VERIFIED).is(false));
                default -> {
                }
            }
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }
        return query;
    }

    private Sort.Direction resolveSortDirection(String sortOrder) {
        return ORDER_ASC.equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private String resolveAdminSortField(String sortField) {
        return ADMIN_SORT_FIELDS.contains(sortField) ? sortField : ServerFields.CREATED_AT;
    }

    public long countAdminServers(String search, String plan, String status) {
        return count(buildAdminServerFilterQuery(search, plan, status));
    }

    public boolean deleteByServerId(String serverId) {
        return remove(Query.query(Criteria.where(ServerFields.ID).is(serverId))).getDeletedCount() > 0;
    }

    public long deleteByServerIds(List<String> serverIds) {
        return remove(Query.query(Criteria.where(ServerFields.ID).in(serverIds))).getDeletedCount();
    }

    public long bulkSuspend(List<String> serverIds, Date updatedAt) {
        Update update = new Update()
            .set(ServerFields.PROVISIONING_STATUS, ProvisioningStatus.FAILED)
            .set(ServerFields.UPDATED_AT, updatedAt);
        return updateMulti(Query.query(Criteria.where(ServerFields.ID).in(serverIds)), update).getModifiedCount();
    }

    public long bulkActivate(List<String> serverIds, Date updatedAt) {
        Update update = new Update()
            .set(ServerFields.PROVISIONING_STATUS, ProvisioningStatus.IN_PROGRESS)
            .set(ServerFields.EMAIL_VERIFIED, true)
            .set(ServerFields.UPDATED_AT, updatedAt);
        return updateMulti(Query.query(Criteria.where(ServerFields.ID).in(serverIds)), update).getModifiedCount();
    }

    public Optional<Server> applyFieldUpdate(String serverId, Update update) {
        if (update.getUpdateObject().isEmpty()) {
            return findById(serverId);
        }
        return Optional.ofNullable(findAndModify(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            update,
            FindAndModifyOptions.options().returnNew(true)
        ));
    }

    public long bulkUpdatePlan(List<String> serverIds, ServerPlan plan, Date updatedAt) {
        Update update = new Update()
            .set(ServerFields.PLAN, plan)
            .set(ServerFields.UPDATED_AT, updatedAt);
        return updateMulti(Query.query(Criteria.where(ServerFields.ID).in(serverIds)), update).getModifiedCount();
    }
}
