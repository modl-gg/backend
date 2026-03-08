package gg.modl.backend.database.mongo;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;

public abstract class AbstractTenantMongoRepository<T> {
    private final Class<T> entityType;
    private final String collectionName;

    protected AbstractTenantMongoRepository(Class<T> entityType, String collectionName) {
        this.entityType = entityType;
        this.collectionName = collectionName;
    }

    protected Optional<T> findById(MongoTemplate template, String id) {
        return Optional.ofNullable(template.findById(id, entityType, collectionName));
    }

    protected T save(MongoTemplate template, T entity) {
        return template.save(entity, collectionName);
    }

    protected Optional<T> findOne(MongoTemplate template, Query query) {
        return Optional.ofNullable(template.findOne(query, entityType, collectionName));
    }

    protected List<T> find(MongoTemplate template, Query query) {
        return template.find(query, entityType, collectionName);
    }

    protected List<T> findAll(MongoTemplate template) {
        return template.findAll(entityType, collectionName);
    }

    protected long count(MongoTemplate template, Query query) {
        return template.count(query, entityType, collectionName);
    }

    protected boolean exists(MongoTemplate template, Query query) {
        return template.exists(query, entityType, collectionName);
    }

    protected UpdateResult updateFirst(MongoTemplate template, Query query, Update update) {
        return template.updateFirst(query, update, entityType, collectionName);
    }

    protected UpdateResult updateMulti(MongoTemplate template, Query query, Update update) {
        return template.updateMulti(query, update, entityType, collectionName);
    }

    protected UpdateResult upsert(MongoTemplate template, Query query, Update update) {
        return template.upsert(query, update, entityType, collectionName);
    }

    protected boolean deleteById(MongoTemplate template, String id) {
        Query query = Query.query(Criteria.where("_id").is(id));
        return template.remove(query, entityType, collectionName).getDeletedCount() > 0;
    }

    protected DeleteResult remove(MongoTemplate template, Query query) {
        return template.remove(query, entityType, collectionName);
    }

    protected T findAndModify(MongoTemplate template, Query query, Update update, FindAndModifyOptions options) {
        return template.findAndModify(query, update, options, entityType, collectionName);
    }

    protected T findAndRemove(MongoTemplate template, Query query) {
        return template.findAndRemove(query, entityType, collectionName);
    }

    protected <O> AggregationResults<O> aggregate(MongoTemplate template, Aggregation aggregation, Class<O> outputType) {
        return template.aggregate(aggregation, collectionName, outputType);
    }

    protected MongoTemplate rawTemplate(MongoTemplate template) {
        return template;
    }

    protected Class<T> entityType() {
        return entityType;
    }

    protected String collectionName() {
        return collectionName;
    }
}
