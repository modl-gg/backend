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
    void replayLiteUploadAndConfirmUseDedicatedTier() {
        assertEquals(
            RateLimitConfig.RateLimitTier.REPLAY_LITE_UPLOAD,
            config.getTierForPath("/v1/replay-lite/replays/upload", "POST")
        );
        assertEquals(
            RateLimitConfig.RateLimitTier.REPLAY_LITE_UPLOAD,
            config.getTierForPath("/v1/replay-lite/replays/75f4b741-67df-414c-957b-a8a08222fc30/confirm", "POST")
        );
    }

    @Test
    void publicReplayLiteLabelUsesDedicatedTier() {
        assertEquals(
            RateLimitConfig.RateLimitTier.REPLAY_LITE_LABEL,
            config.getTierForPath("/v1/public/replay-lite/replays/75f4b741-67df-414c-957b-a8a08222fc30/label", "POST")
        );
    }

    @Test
    void publicReplayLiteReadRemainsStandardTier() {
        assertEquals(
            RateLimitConfig.RateLimitTier.PUBLIC_STANDARD,
            config.getTierForPath("/v1/public/replay-lite/replays/75f4b741-67df-414c-957b-a8a08222fc30", "GET")
        );
    }
}
