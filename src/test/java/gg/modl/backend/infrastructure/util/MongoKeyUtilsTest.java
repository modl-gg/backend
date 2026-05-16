package gg.modl.backend.infrastructure.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gg.modl.backend.infrastructure.exception.ValidationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MongoKeyUtilsTest {
    @Test
    void rejectsDollarPrefixedKeysAtAnyDepth() {
        Map<String, Object> data = Map.of("outer", List.of(Map.of("$set", "value")));

        assertThrows(ValidationException.class, () -> MongoKeyUtils.sanitizeKeys(data));
    }

    @Test
    void rejectsDottedKeys() {
        assertThrows(ValidationException.class, () -> MongoKeyUtils.sanitizeKeys(Map.of("a.b", "value")));
    }

    @Test
    void updatePathAllowsSafeSegmentsOnly() {
        assertDoesNotThrow(() -> MongoKeyUtils.validateUpdatePath("data.status"));
        assertThrows(ValidationException.class, () -> MongoKeyUtils.validateUpdatePath("data.$set"));
    }
}
