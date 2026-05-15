package gg.modl.backend.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.modl.backend.infrastructure.ratelimit.RateLimitConfig;
import org.junit.jupiter.api.Test;

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

    @Test
    void v3MinecraftUsesMinecraftTier() {
        assertEquals(
            RateLimitConfig.RateLimitTier.MINECRAFT_STANDARD,
            config.getTierForPath("/v3/minecraft/players/sync", "POST")
        );
    }

    @Test
    void v3MinecraftLoginUsesLoginTier() {
        assertEquals(
            RateLimitConfig.RateLimitTier.MINECRAFT_LOGIN,
            config.getTierForPath("/v3/minecraft/players/login", "POST")
        );
    }
}

