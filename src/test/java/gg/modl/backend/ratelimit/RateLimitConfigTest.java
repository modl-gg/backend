package gg.modl.backend.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.modl.backend.infrastructure.ratelimit.BucketPool;
import gg.modl.backend.infrastructure.ratelimit.RateLimitConfig;
import org.junit.jupiter.api.Test;

class RateLimitConfigTest {

    private final RateLimitConfig config = new RateLimitConfig(new BucketPool());

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

    @Test
    void v1MinecraftLoginUsesLoginTier() {
        assertEquals(
            RateLimitConfig.RateLimitTier.MINECRAFT_LOGIN,
            config.getTierForPath("/v1/minecraft/players/login", "POST")
        );
    }

    @Test
    void nonLoginMinecraftPlayerRoutesStayStandardTier() {
        assertEquals(
            RateLimitConfig.RateLimitTier.MINECRAFT_STANDARD,
            config.getTierForPath("/v1/minecraft/players/lookup", "POST")
        );
        assertEquals(
            RateLimitConfig.RateLimitTier.MINECRAFT_STANDARD,
            config.getTierForPath("/v1/minecraft/players/disconnect", "POST")
        );
        assertEquals(
            RateLimitConfig.RateLimitTier.MINECRAFT_STANDARD,
            config.getTierForPath("/v1/minecraft/players/pardon", "POST")
        );
        assertEquals(
            RateLimitConfig.RateLimitTier.MINECRAFT_STANDARD,
            config.getTierForPath("/v1/minecraft/players/75f4b741-67df-414c-957b-a8a08222fc30/notes", "POST")
        );
        assertEquals(
            RateLimitConfig.RateLimitTier.MINECRAFT_STANDARD,
            config.getTierForPath("/v1/minecraft/players/75f4b741-67df-414c-957b-a8a08222fc30", "GET")
        );
        assertEquals(
            RateLimitConfig.RateLimitTier.MINECRAFT_STANDARD,
            config.getTierForPath("/v1/minecraft/punishments/create", "POST")
        );
    }

    @Test
    void publicTicketUnfinishedUsesCreateTier() {
        assertEquals(
            RateLimitConfig.RateLimitTier.PUBLIC_TICKET_CREATE,
            config.getTierForPath("/v1/public/tickets/unfinished", "POST")
        );
    }

    @Test
    void publicTicketReplyAndSubmitUseInteractTier() {
        assertEquals(
            RateLimitConfig.RateLimitTier.PUBLIC_TICKET_INTERACT,
            config.getTierForPath("/v1/public/tickets/PLAYER-123456/replies", "POST")
        );
        assertEquals(
            RateLimitConfig.RateLimitTier.PUBLIC_TICKET_INTERACT,
            config.getTierForPath("/v1/public/tickets/PLAYER-123456/submit", "POST")
        );
    }

    @Test
    void publicTicketVerifyUsesVerifyTier() {
        assertEquals(
            RateLimitConfig.RateLimitTier.PUBLIC_TICKET_VERIFY,
            config.getTierForPath("/v1/public/tickets/PLAYER-123456/verify", "POST")
        );
        assertEquals(
            RateLimitConfig.RateLimitTier.PUBLIC_TICKET_VERIFY,
            config.getTierForPath("/v1/public/tickets/PLAYER-123456/request-verification", "POST")
        );
    }

    @Test
    void publicTicketReadRemainsStandardTier() {
        assertEquals(
            RateLimitConfig.RateLimitTier.PUBLIC_STANDARD,
            config.getTierForPath("/v1/public/tickets/PLAYER-123456", "GET")
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
