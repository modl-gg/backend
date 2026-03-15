package gg.modl.backend.database.mongo.codegen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Repeatable(MongoFieldAliases.class)
public @interface MongoFieldAlias {
    String name();

    String path();
}
