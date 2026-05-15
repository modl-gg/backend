package gg.modl.backend.replaylite.data;

import java.util.Locale;
import java.util.Optional;

public enum ReplayLiteLabelVerdict {
    LEGIT,
    SCAFFOLD,
    AIMBOT,
    OTHER;

    public static final String VALIDATION_PATTERN = "(?i)legit|scaffold|aimbot|other";

    public static Optional<ReplayLiteLabelVerdict> from(String verdict) {
        if (verdict == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(ReplayLiteLabelVerdict.valueOf(verdict.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
