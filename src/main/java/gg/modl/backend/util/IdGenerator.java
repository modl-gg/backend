package gg.modl.backend.util;

import java.security.SecureRandom;

/**
 * Utility class for generating short, human-readable IDs.
 */
public final class IdGenerator {
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ID_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
        // Utility class
    }

    /**
     * Generate an 8-character alphanumeric ID (all caps).
     * Example: "A1B2C3D4"
     */
    public static String generatePunishmentId() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * Generate a short ID with custom length.
     */
    public static String generateId(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
