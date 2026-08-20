package gg.modl.backend.infrastructure.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresPanelPermission {
    String value() default "";

    String view() default "";

    String modify() default "";

    PanelAccessRule rule() default PanelAccessRule.REQUIRE_PERMISSION;

    String[] supersedesPermissions() default {};
}
