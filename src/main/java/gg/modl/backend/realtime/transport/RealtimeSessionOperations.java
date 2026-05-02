package gg.modl.backend.realtime.transport;

import gg.modl.backend.realtime.lifecycle.RealtimeConnectionCleanup;
import gg.modl.backend.realtime.metrics.RealtimeMetrics;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class RealtimeSessionOperations {
    private final RealtimeConnectionRegistry connectionRegistry;
    private final RealtimeConnectionCleanup connectionCleanup;
    private final RealtimeMetrics metrics;

    public boolean send(WebSocketSession session, RealtimeConnectionState state, BinaryMessage message) throws IOException {
        WebSocketSession outboundSession = connectionRegistry.getSession(session).orElse(session);
        synchronized (state.getSendLock()) {
            if (state.isClosing() || connectionRegistry.isTerminal(session) || connectionRegistry.isTerminal(outboundSession)) {
                return false;
            }
            if (!outboundSession.isOpen()) {
                connectionCleanup.unregister(outboundSession, CloseStatus.NO_CLOSE_FRAME);
                return false;
            }
            try {
                outboundSession.sendMessage(message);
                metrics.recordTransportSendSuccess(state);
                return true;
            } catch (IOException | RuntimeException exception) {
                metrics.recordTransportSendFailure(state, exception);
                requestClose(session, state, CloseStatus.SERVER_ERROR, "send_failed");
                throw exception;
            }
        }
    }

    public boolean requestClose(WebSocketSession session, RealtimeConnectionState state, CloseStatus status, String metricReason) {
        WebSocketSession outboundSession = connectionRegistry.getSession(session).orElse(session);
        state.markClosing();
        connectionRegistry.markTerminal(session);
        connectionRegistry.markTerminal(outboundSession);
        synchronized (state.getSendLock()) {
            if (!outboundSession.isOpen()) {
                connectionCleanup.unregister(outboundSession, status);
                return true;
            }
            try {
                outboundSession.close(status);
                metrics.recordReconnectClose(state, metricReason);
                if (!outboundSession.isOpen()) {
                    connectionCleanup.unregister(outboundSession, status);
                    return true;
                }
            } catch (IOException | RuntimeException exception) {
                metrics.recordReconnectClose(state, metricReason + "_close_failed");
                if (!outboundSession.isOpen()) {
                    connectionCleanup.unregister(outboundSession, status);
                    return true;
                }
            }
            return false;
        }
    }
}
