package gg.modl.backend.player;

import java.util.UUID;

public final class PlayerDocumentIdGenerator {
    private PlayerDocumentIdGenerator() {}

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
