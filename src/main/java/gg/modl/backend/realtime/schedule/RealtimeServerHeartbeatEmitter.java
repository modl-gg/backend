package gg.modl.backend.realtime.schedule;

import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import gg.modl.backend.realtime.transport.RealtimeCodec;
import gg.modl.backend.realtime.transport.RealtimeSessionOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Emits unsolicited heartbeats to authenticated connections so idle sessions keep seeing inbound
 * traffic.
 *
 * <p>Clients treat prolonged inbound silence as a dead connection. Without this, a connection that
 * is healthy and fully subscribed but simply has no domain events to carry looks dead to the
 * client: the Minecraft plugin tears it down and reconnects roughly every 100 seconds, re-running a
 * full baseline fetch each cycle. {@link RealtimeHeartbeatSweeper} is the inbound counterpart — it
 * closes connections whose <em>client</em> heartbeats have stopped.</p>
 */
@Component
@RequiredArgsConstructor
public class RealtimeServerHeartbeatEmitter {
    private final RealtimeProperties properties;
    private final RealtimeConnectionRegistry connectionRegistry;
    private final RealtimeCodec codec;
    private final RealtimeSessionOperations sessionOperations;

    @Scheduled(fixedDelayString = "${modl.realtime.ws.server-heartbeat-interval-ms:25000}")
    public void emitHeartbeats() {
        if (!properties.isEnabled()) {
            return;
        }

        for (RealtimeConnectionRegistry.RealtimeConnectionSnapshot snapshot : connectionRegistry.snapshot()) {
            RealtimeConnectionState state = snapshot.state();
            // Unauthenticated sessions are the handshake sweeper's business; sending to a closing or
            // terminal session would only race its close frame.
            if (!state.isAuthenticated() || state.isClosing() || state.getTerminalSince() != null) {
                continue;
            }
            WebSocketSession session = snapshot.session();
            if (!session.isOpen()) {
                continue;
            }
            // Best effort: a failed keepalive is not itself grounds for tearing down the connection.
            // A genuinely dead peer stops sending client heartbeats and the sweeper closes it.
            sessionOperations.trySend(session, state, codec.heartbeat(state.nextOutboundHeartbeatSequence()));
        }
    }
}
