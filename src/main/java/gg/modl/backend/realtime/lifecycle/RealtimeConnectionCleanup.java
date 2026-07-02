package gg.modl.backend.realtime.lifecycle;

import gg.modl.backend.realtime.metrics.RealtimeMetrics;
import gg.modl.backend.realtime.rate.RealtimeMessageRateLimiter;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class RealtimeConnectionCleanup {
    private final RealtimeConnectionRegistry connectionRegistry;
    private final RealtimeMessageRateLimiter rateLimiter;
    private final RealtimeMetrics metrics;

    public void unregister(WebSocketSession session) {
        connectionRegistry.unregister(session).ifPresent(rateLimiter::forget);
    }

    public void unregister(WebSocketSession session, CloseStatus status) {
        // Tombstone the session as terminal before removal so a late inbound frame cannot
        // re-register a phantom connection via stateForIncomingFrame.
        connectionRegistry.markTerminal(session);
        connectionRegistry.unregister(session).ifPresent(state -> {
            rateLimiter.forget(state);
            metrics.recordDisconnect(status);
        });
    }

    public void unregisterAfterTransportError(WebSocketSession session, CloseStatus status, Throwable exception) {
        connectionRegistry.markTerminal(session);
        connectionRegistry.unregister(session).ifPresent(state -> {
            metrics.recordTransportError(state, exception);
            rateLimiter.forget(state);
            metrics.recordDisconnect(status);
        });
    }

    public void recordTransportError(RealtimeConnectionState state, Throwable exception) {
        metrics.recordTransportError(state, exception);
    }
}
