package gg.modl.backend.util;

import java.security.SecureRandom;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class IdGenerator {
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ID_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        final StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }

        return sb.toString();
    }
}
