package gg.modl.backend.settings.data;

import org.jetbrains.annotations.Nullable;

public enum PunishmentCategory {
    KICK,
    MUTE,
    BAN;

    public static final int MIN_CORE_ORDINAL = 0;
    public static final int MAX_CORE_ORDINAL = 5;

    public static boolean isCoreOrdinal(@Nullable Integer ordinal) {
        return ordinal != null && ordinal >= MIN_CORE_ORDINAL && ordinal <= MAX_CORE_ORDINAL;
    }

    @Nullable
    public static PunishmentCategory fromCoreOrdinal(@Nullable Integer ordinal) {
        if (!isCoreOrdinal(ordinal)) {
            return null;
        }
        if (ordinal == 0) {
            return KICK;
        }
        if (ordinal == 1) {
            return MUTE;
        }
        return BAN;
    }
}
