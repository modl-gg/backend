package gg.modl.backend.database.mongo;

import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MongoEntityUpdatePlan {
    private final Map<String, Object> setOperations;
    private final List<String> unsetOperations;

    MongoEntityUpdatePlan(Map<String, Object> setOperations, List<String> unsetOperations) {
        this.setOperations = Collections.unmodifiableMap(new LinkedHashMap<>(setOperations));
        this.unsetOperations = Collections.unmodifiableList(new ArrayList<>(unsetOperations));
    }

    public boolean hasChanges() {
        return !setOperations.isEmpty() || !unsetOperations.isEmpty();
    }

    public Map<String, Object> setOperations() {
        return setOperations;
    }

    public List<String> unsetOperations() {
        return unsetOperations;
    }

    public Update toUpdate() {
        Update update = new Update();
        setOperations.forEach(update::set);
        unsetOperations.forEach(update::unset);
        return update;
    }
}
