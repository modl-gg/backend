package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.MongoUpdates;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.StaffRoleFields;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class StaffRoleMongoRepository extends AbstractServerMongoRepository<StaffRole> {
    public StaffRoleMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(StaffRole.class, CollectionName.STAFF_ROLES, tenantMongoAccess);
    }

    public List<StaffRole> findAllOrdered(Server server) {
        Query query = new Query().with(Sort.by(
            Sort.Direction.ASC,
            StaffRoleFields.ORDER,
            StaffRoleFields.CREATED_AT
        ));
        return find(server, query);
    }

    public Optional<StaffRole> findHighestOrdered(Server server) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, StaffRoleFields.ORDER)).limit(1);
        return findOne(server, query);
    }

    public boolean existsByNameIgnoreCase(Server server, String roleName) {
        return exists(server, Query.query(
            MongoQueries.where(StaffRoleFields.NAME).regex("^" + Pattern.quote(roleName) + "$", "i")
        ));
    }

    public boolean existsByNameIgnoreCaseExcludingId(Server server, String roleName, String excludedRoleId) {
        Criteria criteria = MongoQueries.where(StaffRoleFields.NAME)
            .regex("^" + Pattern.quote(roleName) + "$", "i")
            .and(StaffRoleFields.ID).ne(excludedRoleId);
        return exists(server, Query.query(criteria));
    }

    public boolean existsByName(Server server, String roleName) {
        return exists(server, Query.query(MongoQueries.where(StaffRoleFields.NAME).is(roleName)));
    }

    public void updateOrder(Server server, String roleId, int order) {
        Update update = new Update();
        MongoUpdates.set(update, StaffRoleFields.ORDER, order);
        updateFirst(server, Query.query(MongoQueries.where(StaffRoleFields.ID).is(roleId)), update);
    }

    public void upsertRole(Server server, StaffRole role) {
        Query query = Query.query(MongoQueries.where(StaffRoleFields.ID).is(role.getId()));
        Update update = new Update();
        MongoUpdates.set(update, StaffRoleFields.NAME, role.getName());
        MongoUpdates.set(update, StaffRoleFields.DESCRIPTION, role.getDescription());
        MongoUpdates.set(update, StaffRoleFields.PERMISSIONS, role.getPermissions());
        MongoUpdates.set(update, StaffRoleFields.IS_DEFAULT, role.isDefault());
        MongoUpdates.set(update, StaffRoleFields.ORDER, role.getOrder());
        MongoUpdates.setOnInsert(update, StaffRoleFields.CREATED_AT, role.getCreatedAt());
        MongoUpdates.set(update, StaffRoleFields.UPDATED_AT, role.getUpdatedAt());
        upsert(server, query, update);
    }

    public List<StaffRole> findCustomRolesWithOrderZero(Server server) {
        Query query = Query.query(MongoQueries.where(StaffRoleFields.IS_DEFAULT).is(false)
            .and(StaffRoleFields.ORDER).is(0));
        return find(server, query);
    }

    public boolean deleteById(Server server, String roleId) {
        return remove(server, Query.query(MongoQueries.where(StaffRoleFields.ID).is(roleId))).getDeletedCount() > 0;
    }

    public List<StaffRole> findByNames(Server server, Collection<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return List.of();
        }
        return find(server, Query.query(MongoQueries.where(StaffRoleFields.NAME).in(roleNames)));
    }

    public Optional<StaffRole> findByName(Server server, String roleName) {
        return findOne(server, Query.query(MongoQueries.where(StaffRoleFields.NAME).is(roleName)));
    }

    public List<StaffRole> findByIds(Server server, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return find(server, Query.query(MongoQueries.where(StaffRoleFields.ID).in(ids)));
    }

    public void bulkUpdateOrder(Server server, Map<String, Integer> orderById) {
        if (orderById.isEmpty()) return;

        MongoTemplate template = serverTemplate(server);
        BulkOperations bulk = template.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName());
        for (Map.Entry<String, Integer> entry : orderById.entrySet()) {
            Query query = Query.query(Criteria.where("_id").is(entry.getKey()));
            Update update = new Update().set(StaffRoleFields.ORDER, entry.getValue());
            bulk.updateOne(query, update);
        }
        bulk.execute();
    }
}

