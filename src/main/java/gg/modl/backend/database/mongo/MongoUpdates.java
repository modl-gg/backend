package gg.modl.backend.database.mongo;

import org.springframework.data.mongodb.core.query.Update;

public final class MongoUpdates {
    private MongoUpdates() {
    }

    public static Update set(Update update, String field, Object value) {
        return update.set(field, value);
    }

    public static Update setOnInsert(Update update, String field, Object value) {
        return update.setOnInsert(field, value);
    }

    public static Update unset(Update update, String field) {
        return update.unset(field);
    }

    public static Update push(Update update, String field, Object value) {
        return update.push(field, value);
    }

    public static Update addToSet(Update update, String field, Object value) {
        return update.addToSet(field, value);
    }

    public static Update pull(Update update, String field, Object value) {
        return update.pull(field, value);
    }

    public static Update inc(Update update, String field, Number delta) {
        return update.inc(field, delta);
    }
}
