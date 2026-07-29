package gg.modl.backend.replaylite.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.ratelimit.BucketPool;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplayLiteAbuseGuardTest {

    private static final int INIT_LIMIT = 40;
    private static final int IP_LIMIT = 120;

    private ReplayLiteAbuseGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ReplayLiteAbuseGuard(new BucketPool());
    }

    @Test
    void initAttemptsUnderLimitAreAllowed() {
        UUID server = UUID.randomUUID();
        assertDoesNotThrow(() -> {
            for (int attempt = 0; attempt < INIT_LIMIT; attempt++) {
                guard.checkInit(server);
            }
        });
    }

    @Test
    void initAttemptsOverLimitRejectWithFrozenMessage() {
        UUID server = UUID.randomUUID();
        for (int attempt = 0; attempt < INIT_LIMIT; attempt++) {
            guard.checkInit(server);
        }

        ValidationException exception = assertThrows(ValidationException.class, () -> guard.checkInit(server));
        assertEquals("Too many Replay Lite upload attempts", exception.getMessage());
    }

    @Test
    void initLimitIsIsolatedPerServer() {
        UUID exhausted = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        for (int attempt = 0; attempt < INIT_LIMIT; attempt++) {
            guard.checkInit(exhausted);
        }
        assertThrows(ValidationException.class, () -> guard.checkInit(exhausted));

        assertDoesNotThrow(() -> guard.checkInit(fresh));
    }

    @Test
    void ipLimitIsIsolatedPerAddress() {
        String exhausted = "203.0.113.10";
        String fresh = "203.0.113.11";
        for (int attempt = 0; attempt < IP_LIMIT; attempt++) {
            guard.checkIp(exhausted);
        }

        ValidationException exception = assertThrows(ValidationException.class, () -> guard.checkIp(exhausted));
        assertEquals("Too many Replay Lite requests", exception.getMessage());
        assertDoesNotThrow(() -> guard.checkIp(fresh));
    }
}
