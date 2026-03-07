package gg.modl.backend.database.mongo;

import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

final class PropertyRefResolver {
    private PropertyRefResolver() {
    }

    static String resolvePropertyName(PropertyRef<?, ?> propertyRef) {
        SerializedLambda lambda = extractSerializedLambda(propertyRef);
        return toPropertyName(lambda.getImplMethodName());
    }

    private static SerializedLambda extractSerializedLambda(PropertyRef<?, ?> propertyRef) {
        try {
            Method method = propertyRef.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            return (SerializedLambda) method.invoke(propertyRef);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Unable to resolve property reference", exception);
        }
    }

    private static String toPropertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Introspector.decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Introspector.decapitalize(methodName.substring(2));
        }
        if (!methodName.isBlank()) {
            return methodName;
        }
        throw new IllegalArgumentException("Unsupported accessor method: " + methodName);
    }
}
