package gg.modl.backend.realtime.rate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import org.junit.jupiter.api.Test;

class RealtimeMessageRateLimiterTest {

    @Test
    void rejectsMessagesAfterConnectionExceedsWindowLimit() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setInboundRateLimitMessages(2);
        properties.setInboundRateLimitWindowSeconds(60);
        RealtimeMessageRateLimiter limiter = new RealtimeMessageRateLimiter(properties);
        RealtimeConnectionState state = new RealtimeConnectionState();

        assertTrue(limiter.tryAcquire(state));
        assertTrue(limiter.tryAcquire(state));
        assertFalse(limiter.tryAcquire(state));
    }
}
