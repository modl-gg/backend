package gg.modl.backend.realtime.schedule;

import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.realtime.lifecycle.RealtimeConnectionCleanup;
import gg.modl.backend.realtime.metrics.RealtimeMetrics;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import gg.modl.backend.realtime.transport.RealtimeSessionOperations;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class RealtimeHeartbeatSweeper {
    private static final CloseStatus HEARTBEAT_TIMEOUT = new CloseStatus(1001, "Realtime heartbeat timed out");
    private static final CloseStatus HANDSHAKE_TIMEOUT = new CloseStatus(1008, "Realtime ClientHello timed out");

    private final RealtimeProperties properties;
    private final RealtimeConnectionRegistry connectionRegistry;
    private final RealtimeConnectionCleanup connectionCleanup;
    private final RealtimeMetrics metrics;
    private final RealtimeSessionOperations sessionOperations;

    @Scheduled(fixedDelayString = "${modl.realtime.ws.heartbeat-sweep-interval-ms:15000}")
    public void closeTimedOutSessions() {
        if (!properties.isEnabled()) {
            return;
        }

        Instant now = Instant.now();
        Instant heartbeatCutoff = now.minusSeconds(properties.getHeartbeatTimeoutSeconds());
        Instant handshakeCutoff = now.minusSeconds(properties.getHandshakeTimeoutSeconds());
        for (RealtimeConnectionRegistry.RealtimeConnectionSnapshot snapshot : connectionRegistry.snapshot()) {
            RealtimeConnectionState state = snapshot.state();
            WebSocketSession session = snapshot.session();
            if (!session.isOpen()) {
                connectionCleanup.unregister(session, CloseStatus.NO_CLOSE_FRAME);
                continue;
            }
            if (!state.isAuthenticated()) {
                if (state.getConnectedAt().isBefore(handshakeCutoff)) {
                    metrics.recordReject(state, "handshake_timeout");
                    close(session, state, HANDSHAKE_TIMEOUT);
                }
                continue;
            }
            if (!state.getLastHeartbeat().isBefore(heartbeatCutoff)) {
                continue;
            }

            metrics.recordTimeoutClose(state);
            close(session, state, HEARTBEAT_TIMEOUT);
        }
    }

    private void close(WebSocketSession session, RealtimeConnectionState state, CloseStatus status) {
        sessionOperations.requestClose(session, state, status, "heartbeat_timeout");
    }
}
