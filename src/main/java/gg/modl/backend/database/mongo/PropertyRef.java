package gg.modl.backend.database.mongo;

import java.io.Serializable;
import java.util.function.Function;

@FunctionalInterface
public interface PropertyRef<T, R> extends Function<T, R>, Serializable {
}
