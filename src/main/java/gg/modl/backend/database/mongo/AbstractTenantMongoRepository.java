package gg.modl.backend.database.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractTenantMongoRepository<T> {
    private static final ConcurrentMap<Class<?>, Field> ID_FIELDS = new ConcurrentHashMap<>();

    private final Class<T> entityType;
    private final String collectionName;
    private final MongoEntityDiffService diffService;

    protected AbstractTenantMongoRepository(Class<T> entityType, String collectionName, MongoEntityDiffService diffService) {
        this.entityType = entityType;
        this.collectionName = collectionName;
        this.diffService = diffService;
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

    protected T saveChanges(MongoTemplate template, T original, T updated) {
        Objects.requireNonNull(updated, "Updated entity must not be null");

        Object id = extractId(updated);
        if (id == null) {
            return save(template, updated);
        }

        MongoEntityUpdatePlan updatePlan = diffService.diff(original, updated);
        if (!updatePlan.hasChanges()) {
            return updated;
        }

        Query query = Query.query(Criteria.where("_id").is(id));
        template.updateFirst(query, updatePlan.toUpdate(), entityType, collectionName);
        return template.findById(id, entityType, collectionName);
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

    public T snapshot(T entity) {
        return diffService.snapshot(entity, entityType);
    }

    private Object extractId(T entity) {
        Field idField = ID_FIELDS.computeIfAbsent(entity.getClass(), AbstractTenantMongoRepository::findIdField);
        if (idField == null) {
            throw new IllegalStateException("No @Id field found for " + entity.getClass().getName());
        }

        try {
            return idField.get(entity);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read @Id field for " + entity.getClass().getName(), exception);
        }
    }

    private static Field findIdField(Class<?> entityType) {
        Class<?> currentType = entityType;
        while (currentType != null && currentType != Object.class) {
            for (Field field : currentType.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            currentType = currentType.getSuperclass();
        }
        return null;
    }
}
