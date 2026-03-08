package gg.modl.backend.database.mongo;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public final class MongoQueries {
    private MongoQueries() {
    }

    public static Criteria where(String field) {
        return Criteria.where(field);
    }

    public static Sort sort(Sort.Direction direction, String... fields) {
        return Sort.by(direction, fields);
    }

    public static Query include(Query query, String... fields) {
        for (String field : fields) {
            query.fields().include(field);
        }
        return query;
    }
}
