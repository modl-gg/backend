package gg.modl.backend.realtime.rate;

import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RealtimeMessageRateLimiter {
    private final RealtimeProperties properties;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RealtimeMessageRateLimiter(RealtimeProperties properties) {
        this(properties, Clock.systemUTC());
    }

    RealtimeMessageRateLimiter(RealtimeProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean tryAcquire(RealtimeConnectionState state) {
        Window window = windows.computeIfAbsent(state.getConnectionId(), ignored -> new Window(clock.instant()));
        synchronized (window) {
            Instant now = clock.instant();
            Duration elapsed = Duration.between(window.startedAt, now);
            if (!elapsed.isNegative() && elapsed.getSeconds() >= properties.getInboundRateLimitWindowSeconds()) {
                window.startedAt = now;
                window.count = 0;
            }

            if (window.count >= properties.getInboundRateLimitMessages()) {
                return false;
            }

            window.count++;
            return true;
        }
    }

    public void forget(RealtimeConnectionState state) {
        windows.remove(state.getConnectionId());
    }

    private static final class Window {
        private Instant startedAt;
        private int count;

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}
