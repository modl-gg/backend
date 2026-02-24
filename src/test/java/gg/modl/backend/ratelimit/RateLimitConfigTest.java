package gg.modl.backend.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitConfigTest {

    private final RateLimitConfig config = new RateLimitConfig();

    @Test
    void publicMediaPostUsesDedicatedTier() {
        assertEquals(
                RateLimitConfig.RateLimitTier.PUBLIC_MEDIA_UPLOAD,
                config.getTierForPath("/v1/public/media/presign", "POST")
        );
        assertEquals(
                RateLimitConfig.RateLimitTier.PUBLIC_MEDIA_UPLOAD,
                config.getTierForPath("/v1/public/media/confirm", "POST")
        );
    }

    @Test
    void publicMediaReadRemainsStandardTier() {
        assertEquals(
                RateLimitConfig.RateLimitTier.PUBLIC_STANDARD,
                config.getTierForPath("/v1/public/media/config", "GET")
        );
    }

    @Test
    void publicTicketCreateTierUnchanged() {
        assertEquals(
                RateLimitConfig.RateLimitTier.PUBLIC_TICKET_CREATE,
                config.getTierForPath("/v1/public/tickets", "POST")
        );
    }
}

