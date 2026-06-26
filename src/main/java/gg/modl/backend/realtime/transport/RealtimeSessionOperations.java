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

    private enum SendOutcome { SENT, CLOSED, FAILED }

    private SendOutcome attemptSend(WebSocketSession session, RealtimeConnectionState state, BinaryMessage message) {
        WebSocketSession outboundSession = connectionRegistry.getSession(session).orElse(session);
        synchronized (state.getSendLock()) {
            if (state.isClosing() || connectionRegistry.isTerminal(session) || connectionRegistry.isTerminal(outboundSession)) {
                return SendOutcome.CLOSED;
            }
            if (!outboundSession.isOpen()) {
                connectionCleanup.unregister(outboundSession, CloseStatus.NO_CLOSE_FRAME);
                return SendOutcome.CLOSED;
            }
            try {
                outboundSession.sendMessage(message);
                metrics.recordTransportSendSuccess(state);
                return SendOutcome.SENT;
            } catch (IOException | RuntimeException exception) {
                metrics.recordTransportSendFailure(state, exception);
                return SendOutcome.FAILED;
            }
        }
    }

    /**
     * Best-effort dispatch (fan-out): drops a stuck client by closing it on failure. Never throws.
     */
    public boolean deliver(WebSocketSession session, RealtimeConnectionState state, BinaryMessage message) {
        SendOutcome outcome = attemptSend(session, state, message);
        if (outcome == SendOutcome.SENT) {
            return true;
        }
        if (outcome == SendOutcome.FAILED) {
            requestClose(session, state, CloseStatus.SERVER_ERROR, "send_failed");
        }
        return false;
    }

    /**
     * Control-path send: never closes the session and never throws. The caller keeps ownership of
     * the close and its intended status.
     */
    public boolean trySend(WebSocketSession session, RealtimeConnectionState state, BinaryMessage message) {
        return attemptSend(session, state, message) == SendOutcome.SENT;
    }

    public boolean requestClose(WebSocketSession session, RealtimeConnectionState state, CloseStatus status, String metricReason) {
        WebSocketSession outboundSession = connectionRegistry.getSession(session).orElse(session);
        state.markClosing();
        state.markTerminal();
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
