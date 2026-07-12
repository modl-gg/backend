package gg.modl.backend.database.mongo;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.server.data.Server;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public abstract class AbstractServerMongoRepository<T> extends AbstractTenantMongoRepository<T> {
    private final TenantMongoAccess tenantMongoAccess;

    protected AbstractServerMongoRepository(
        Class<T> entityType,
        String collectionName,
        TenantMongoAccess tenantMongoAccess
    ) {
        super(entityType, collectionName);
        this.tenantMongoAccess = tenantMongoAccess;
    }

    public Optional<T> findById(Server server, String id) {
        return findById(tenantMongoAccess.forServer(server), id);
    }

    public Optional<T> findOne(Server server, Query query) {
        return findOne(tenantMongoAccess.forServer(server), query);
    }

    public List<T> find(Server server, Query query) {
        return find(tenantMongoAccess.forServer(server), query);
    }

    public List<T> findAll(Server server) {
        return findAll(tenantMongoAccess.forServer(server));
    }

    public long count(Server server, Query query) {
        return count(tenantMongoAccess.forServer(server), query);
    }

    public boolean exists(Server server, Query query) {
        return exists(tenantMongoAccess.forServer(server), query);
    }

    public T saveEntity(Server server, T entity) {
        return save(tenantMongoAccess.forServer(server), entity);
    }

    public T insertEntity(Server server, T entity) {
        return insert(tenantMongoAccess.forServer(server), entity);
    }

    public void insertAll(Server server, Collection<T> entities) {
        insertAll(tenantMongoAccess.forServer(server), entities);
    }

    public UpdateResult updateFirst(Server server, Query query, Update update) {
        return updateFirst(tenantMongoAccess.forServer(server), query, update);
    }

    public UpdateResult updateMulti(Server server, Query query, Update update) {
        return updateMulti(tenantMongoAccess.forServer(server), query, update);
    }

    public UpdateResult upsert(Server server, Query query, Update update) {
        return upsert(tenantMongoAccess.forServer(server), query, update);
    }

    public DeleteResult remove(Server server, Query query) {
        return remove(tenantMongoAccess.forServer(server), query);
    }

    public T findAndModify(Server server, Query query, Update update, FindAndModifyOptions options) {
        return findAndModify(tenantMongoAccess.forServer(server), query, update, options);
    }

    public T findAndRemove(Server server, Query query) {
        return findAndRemove(tenantMongoAccess.forServer(server), query);
    }

    public <O> AggregationResults<O> aggregate(Server server, Aggregation aggregation, Class<O> outputType) {
        return aggregate(tenantMongoAccess.forServer(server), aggregation, outputType);
    }

    protected void bulkUpdateByIds(Server server, List<String> ids, BiFunction<String, Integer, Update> updateFactory) {
        bulkUpdateByIds(server, ids, (id, index) -> Criteria.where("_id").is(id), updateFactory);
    }

    protected void bulkUpdateByIds(
        Server server,
        List<String> ids,
        BiFunction<String, Integer, Criteria> criteriaFactory,
        BiFunction<String, Integer, Update> updateFactory
    ) {
        if (ids.isEmpty()) {
            return;
        }
        BulkOperations bulk = serverTemplate(server).bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName());
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            bulk.updateOne(Query.query(criteriaFactory.apply(id, index)), updateFactory.apply(id, index));
        }
        bulk.execute();
    }

    protected MongoTemplate serverTemplate(Server server) {
        return tenantMongoAccess.forServer(server);
    }
}
