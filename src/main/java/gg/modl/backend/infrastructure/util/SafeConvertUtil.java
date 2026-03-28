package gg.modl.backend.infrastructure.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class SafeConvertUtil {
    public static int toInt(@Nullable Object value) {
        if (value instanceof final Number number) {
            return number.intValue();
        }

        if (value instanceof final String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        return 0;
    }
}
