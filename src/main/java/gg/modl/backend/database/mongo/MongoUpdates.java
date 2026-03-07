package gg.modl.backend.database.mongo;

import org.springframework.data.mongodb.core.query.Update;

public final class MongoUpdates {
    private MongoUpdates() {
    }

    public static Update set(Update update, MongoField<?> field, Object value) {
        return update.set(field.path(), value);
    }

    public static Update setOnInsert(Update update, MongoField<?> field, Object value) {
        return update.setOnInsert(field.path(), value);
    }

    public static Update unset(Update update, MongoField<?> field) {
        return update.unset(field.path());
    }

    public static Update push(Update update, MongoField<?> field, Object value) {
        return update.push(field.path(), value);
    }

    public static Update addToSet(Update update, MongoField<?> field, Object value) {
        return update.addToSet(field.path(), value);
    }

    public static Update pull(Update update, MongoField<?> field, Object value) {
        return update.pull(field.path(), value);
    }

    public static Update inc(Update update, MongoField<?> field, Number delta) {
        return update.inc(field.path(), delta);
    }
}
