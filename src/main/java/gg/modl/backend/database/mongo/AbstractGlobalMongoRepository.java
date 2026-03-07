package gg.modl.backend.database.mongo;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;

public abstract class AbstractGlobalMongoRepository<T> extends AbstractTenantMongoRepository<T> {
    private final TenantMongoAccess tenantMongoAccess;

    protected AbstractGlobalMongoRepository(
            Class<T> entityType,
            String collectionName,
            MongoEntityDiffService diffService,
            TenantMongoAccess tenantMongoAccess
    ) {
        super(entityType, collectionName, diffService);
        this.tenantMongoAccess = tenantMongoAccess;
    }

    public Optional<T> findById(String id) {
        return findById(tenantMongoAccess.global(), id);
    }

    public Optional<T> findOne(Query query) {
        return findOne(tenantMongoAccess.global(), query);
    }

    public List<T> find(Query query) {
        return find(tenantMongoAccess.global(), query);
    }

    public List<T> findAll() {
        return findAll(tenantMongoAccess.global());
    }

    public long count(Query query) {
        return count(tenantMongoAccess.global(), query);
    }

    public boolean exists(Query query) {
        return exists(tenantMongoAccess.global(), query);
    }

    public T saveEntity(T entity) {
        return save(tenantMongoAccess.global(), entity);
    }

    public T saveChanges(T original, T updated) {
        return saveChanges(tenantMongoAccess.global(), original, updated);
    }

    public UpdateResult updateFirst(Query query, Update update) {
        return updateFirst(tenantMongoAccess.global(), query, update);
    }

    public UpdateResult updateMulti(Query query, Update update) {
        return updateMulti(tenantMongoAccess.global(), query, update);
    }

    public UpdateResult upsert(Query query, Update update) {
        return upsert(tenantMongoAccess.global(), query, update);
    }

    public DeleteResult remove(Query query) {
        return remove(tenantMongoAccess.global(), query);
    }

    public <O> AggregationResults<O> aggregate(Aggregation aggregation, Class<O> outputType) {
        return aggregate(tenantMongoAccess.global(), aggregation, outputType);
    }
}
