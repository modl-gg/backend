package gg.modl.backend.infrastructure.util;

import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class SafeConvertUtil {
    public static int toInt(@Nullable Object value) {
        return ProtoMapperSupport.intValueOrZero(value);
    }
}
