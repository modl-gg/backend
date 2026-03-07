package gg.modl.backend.database.mongo;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public final class MongoQueries {
    private MongoQueries() {
    }

    public static Criteria where(MongoField<?> field) {
        return Criteria.where(field.path());
    }

    public static Sort sort(Sort.Direction direction, MongoField<?>... fields) {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("At least one field is required for sorting");
        }

        Sort.Order[] orders = new Sort.Order[fields.length];
        for (int index = 0; index < fields.length; index++) {
            orders[index] = new Sort.Order(direction, fields[index].path());
        }
        return Sort.by(orders);
    }

    public static Query include(Query query, MongoField<?>... fields) {
        for (MongoField<?> field : fields) {
            query.fields().include(field.path());
        }
        return query;
    }
}
