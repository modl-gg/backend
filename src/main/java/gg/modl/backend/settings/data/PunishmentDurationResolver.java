package gg.modl.backend.settings.data;

import java.util.HashMap;
import java.util.Map;

public final class PunishmentDurationResolver {
    private static final Map<Integer, PunishmentType> DEFAULT_TYPES_BY_ORDINAL = indexDefaults();

    private PunishmentDurationResolver() {
    }

    public static DurationDetail resolveDetail(PunishmentType type, String severity, String offenseLevel) {
        if (type == null) {
            return null;
        }
        DurationDetail detail = type.getDurationDetail(severity, offenseLevel);
        if (detail != null) {
            return detail;
        }
        if (type.getOrdinal() == null) {
            return null;
        }
        PunishmentType defaultType = DEFAULT_TYPES_BY_ORDINAL.get(type.getOrdinal());
        return defaultType != null ? defaultType.getDurationDetail(severity, offenseLevel) : null;
    }

    private static Map<Integer, PunishmentType> indexDefaults() {
        Map<Integer, PunishmentType> byOrdinal = new HashMap<>();
        for (PunishmentType type : DefaultPunishmentTypes.getAll()) {
            byOrdinal.put(type.getOrdinal(), type);
        }
        return byOrdinal;
    }
}
