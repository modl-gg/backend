package gg.modl.backend.database.mongo;

import org.bson.Document;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class MongoEntityDiffService {
    private static final String ID_FIELD = "_id";
    private static final String TYPE_FIELD = "_class";
    private static final Set<String> IGNORED_ROOT_FIELDS = Set.of(ID_FIELD, TYPE_FIELD);

    private final MappingMongoConverter mongoConverter;

    public MongoEntityDiffService(MappingMongoConverter mongoConverter) {
        this.mongoConverter = mongoConverter;
    }

    public MongoEntityUpdatePlan diff(Object originalEntity, Object updatedEntity) {
        if (updatedEntity == null) {
            throw new IllegalArgumentException("Updated entity must not be null");
        }

        Document original = toDocument(originalEntity);
        Document updated = toDocument(updatedEntity);

        Map<String, Object> setOperations = new LinkedHashMap<>();
        List<String> unsetOperations = new ArrayList<>();
        diffDocuments("", original, updated, setOperations, unsetOperations, true);
        return new MongoEntityUpdatePlan(setOperations, unsetOperations);
    }

    public <T> T snapshot(T entity, Class<T> entityType) {
        if (entity == null) {
            return null;
        }

        Document document = new Document();
        mongoConverter.write(entity, document);
        return mongoConverter.read(entityType, document);
    }

    private Document toDocument(Object entity) {
        if (entity == null) {
            return new Document();
        }

        Document document = new Document();
        mongoConverter.write(entity, document);
        sanitizeDocument(document);
        return document;
    }

    @SuppressWarnings("unchecked")
    private void diffDocuments(
            String basePath,
            Document original,
            Document updated,
            Map<String, Object> setOperations,
            List<String> unsetOperations,
            boolean root
    ) {
        List<String> keys = new ArrayList<>();
        original.keySet().stream().filter(key -> !shouldIgnoreKey(key, root)).forEach(keys::add);
        updated.keySet().stream()
                .filter(key -> !shouldIgnoreKey(key, root))
                .filter(key -> !keys.contains(key))
                .forEach(keys::add);

        for (String key : keys) {
            String path = basePath.isEmpty() ? key : basePath + '.' + key;
            Object originalValue = original.get(key);
            Object updatedValue = updated.get(key);

            if (originalValue instanceof Document originalDocument && updatedValue instanceof Document updatedDocument) {
                diffDocuments(path, originalDocument, updatedDocument, setOperations, unsetOperations, false);
                continue;
            }

            if (isMissingOrNull(updated, key, updatedValue)) {
                if (!isMissingOrNull(original, key, originalValue)) {
                    unsetOperations.add(path);
                }
                continue;
            }

            if (isMissingOrNull(original, key, originalValue)) {
                setOperations.put(path, normalizeValue(updatedValue));
                continue;
            }

            if (originalValue instanceof Map<?, ?> originalMap && updatedValue instanceof Map<?, ?> updatedMap) {
                diffDocuments(path, new Document((Map<String, Object>) originalMap), new Document((Map<String, Object>) updatedMap), setOperations, unsetOperations, false);
                continue;
            }

            if (originalValue instanceof List<?> originalList && updatedValue instanceof List<?> updatedList) {
                if (!Objects.equals(originalList, updatedList)) {
                    setOperations.put(path, updatedList);
                }
                continue;
            }

            if (!valuesEqual(originalValue, updatedValue)) {
                setOperations.put(path, normalizeValue(updatedValue));
            }
        }
    }

    private boolean shouldIgnoreKey(String key, boolean root) {
        if (TYPE_FIELD.equals(key)) {
            return true;
        }
        return root && IGNORED_ROOT_FIELDS.contains(key);
    }

    private boolean isMissingOrNull(Document document, String key, Object value) {
        return !document.containsKey(key) || value == null;
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left instanceof Date leftDate && right instanceof Date rightDate) {
            return leftDate.getTime() == rightDate.getTime();
        }
        return Objects.equals(left, right);
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Document nestedDocument) {
            sanitizeDocument(nestedDocument);
            return nestedDocument;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private void sanitizeDocument(Document document) {
        document.remove(TYPE_FIELD);
        for (String key : new ArrayList<>(document.keySet())) {
            Object value = document.get(key);
            if (value instanceof Document nestedDocument) {
                sanitizeDocument(nestedDocument);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Document itemDocument) {
                        sanitizeDocument(itemDocument);
                    }
                }
            } else if (value instanceof Map<?, ?> map) {
                Document normalized = new Document((Map<String, Object>) map);
                sanitizeDocument(normalized);
                document.put(key, normalized);
            }
        }
    }
}
