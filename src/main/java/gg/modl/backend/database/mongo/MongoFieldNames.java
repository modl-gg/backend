package gg.modl.backend.database.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MongoFieldNames {
    private static final ConcurrentMap<String, MongoField<?>> CACHE = new ConcurrentHashMap<>();

    private MongoFieldNames() {
    }

    public static <T, R> MongoField<T> field(Class<T> rootType, PropertyRef<T, R> property) {
        return cached(rootType, resolvePath(rootType, rootType, PropertyRefResolver.resolvePropertyName(property)));
    }

    public static <T, A, B> MongoField<T> field(
            Class<T> rootType,
            PropertyRef<T, ? extends Collection<A>> collectionProperty,
            Class<A> elementType,
            PropertyRef<A, B> nestedProperty
    ) {
        return cached(
                rootType,
                resolvePath(
                        rootType,
                        rootType,
                        PropertyRefResolver.resolvePropertyName(collectionProperty),
                        elementType,
                        PropertyRefResolver.resolvePropertyName(nestedProperty)
                )
        );
    }

    public static <T, A, B, C> MongoField<T> field(
            Class<T> rootType,
            PropertyRef<T, ? extends Collection<A>> firstProperty,
            Class<A> firstNestedType,
            PropertyRef<A, ? extends Collection<B>> secondProperty,
            Class<B> secondNestedType,
            PropertyRef<B, C> nestedProperty
    ) {
        return cached(
                rootType,
                resolvePath(
                        rootType,
                        rootType,
                        PropertyRefResolver.resolvePropertyName(firstProperty),
                        firstNestedType,
                        PropertyRefResolver.resolvePropertyName(secondProperty),
                        secondNestedType,
                        PropertyRefResolver.resolvePropertyName(nestedProperty)
                )
        );
    }

    public static <T> MongoField<T> raw(Class<T> rootType, String path) {
        return cached(rootType, path);
    }

    private static <T> MongoField<T> cached(Class<T> rootType, String path) {
        String key = rootType.getName() + ':' + path;
        @SuppressWarnings("unchecked")
        MongoField<T> cached = (MongoField<T>) CACHE.computeIfAbsent(key, ignored -> new MongoField<>(path));
        return cached;
    }

    private static String resolvePath(Class<?> rootType, Class<?> ownerType, Object... segments) {
        StringBuilder builder = new StringBuilder();
        Class<?> currentType = ownerType;

        for (Object segment : segments) {
            if (segment instanceof String propertyName) {
                if (builder.length() > 0) {
                    builder.append('.');
                }
                builder.append(resolveFieldName(currentType, propertyName));
                currentType = resolveNextType(currentType, propertyName, rootType);
            } else if (segment instanceof Class<?> nextType) {
                currentType = nextType;
            } else {
                throw new IllegalArgumentException("Unsupported path segment: " + segment);
            }
        }

        return builder.toString();
    }

    private static String resolveFieldName(Class<?> ownerType, String propertyName) {
        java.lang.reflect.Field field = findField(ownerType, propertyName);
        if (field == null) {
            return propertyName;
        }

        Field fieldAnnotation = field.getAnnotation(Field.class);
        if (fieldAnnotation != null) {
            if (!fieldAnnotation.name().isBlank()) {
                return fieldAnnotation.name();
            }
            if (!fieldAnnotation.value().isBlank()) {
                return fieldAnnotation.value();
            }
        }

        if (field.isAnnotationPresent(Id.class)) {
            return "_id";
        }

        return propertyName;
    }

    private static Class<?> resolveNextType(Class<?> ownerType, String propertyName, Class<?> rootType) {
        java.lang.reflect.Field field = findField(ownerType, propertyName);
        if (field == null) {
            return rootType;
        }

        Class<?> rawType = field.getType();
        if (Map.class.isAssignableFrom(rawType)) {
            return Object.class;
        }
        if (!Collection.class.isAssignableFrom(rawType)) {
            return rawType;
        }

        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType parameterizedType) {
            Type actualType = parameterizedType.getActualTypeArguments()[0];
            if (actualType instanceof Class<?> elementType) {
                return elementType;
            }
            if (actualType instanceof ParameterizedType nestedParameterizedType
                    && nestedParameterizedType.getRawType() instanceof Class<?> nestedRawType) {
                return nestedRawType;
            }
        }

        return Object.class;
    }

    private static java.lang.reflect.Field findField(Class<?> ownerType, String propertyName) {
        Class<?> currentType = ownerType;
        while (currentType != null && currentType != Object.class) {
            try {
                java.lang.reflect.Field field = currentType.getDeclaredField(propertyName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                currentType = currentType.getSuperclass();
            }
        }
        return null;
    }
}
