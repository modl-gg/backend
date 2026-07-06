package gg.modl.backend.infrastructure.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class CanonicalAliasIndex<E extends Enum<E>> {
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_UNDERSCORES = Pattern.compile("^_+|_+$");

    private final Map<String, E> byCanonicalId = new LinkedHashMap<>();
    private final String entityName;

    private CanonicalAliasIndex(String entityName) {
        this.entityName = entityName;
    }

    public static <E extends Enum<E>> CanonicalAliasIndex<E> of(String entityName, E[] values, Function<E, String> idFunction) {
        CanonicalAliasIndex<E> index = new CanonicalAliasIndex<>(entityName);
        for (E value : values) {
            index.byCanonicalId.put(normalize(idFunction.apply(value)), value);
        }
        return index;
    }

    public CanonicalAliasIndex<E> alias(E value, String alias) {
        byCanonicalId.put(normalize(alias), value);
        return this;
    }

    public E resolve(String value) {
        E resolved = byCanonicalId.get(normalize(value));
        if (resolved == null) {
            throw new IllegalArgumentException("Unknown " + entityName + ": " + value);
        }
        return resolved;
    }

    public E resolveOrNull(String value) {
        return byCanonicalId.get(normalize(value));
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lowered = value.trim().toLowerCase(Locale.ROOT);
        String underscored = NON_ALPHANUMERIC.matcher(lowered).replaceAll("_");
        return EDGE_UNDERSCORES.matcher(underscored).replaceAll("");
    }
}
