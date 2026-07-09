package gg.modl.backend.realtime.transport;

import gg.modl.backend.realtime.rate.RealtimeUnauthenticatedConnectionLimiter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.Nullable;

public final class RealtimeUnauthenticatedSlot {
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final String clientIp;

    public RealtimeUnauthenticatedSlot(@Nullable String clientIp) {
        this.clientIp = clientIp;
    }

    public void releaseOnce(RealtimeUnauthenticatedConnectionLimiter limiter) {
        if (active.compareAndSet(true, false)) {
            limiter.release(clientIp);
        }
    }
}
