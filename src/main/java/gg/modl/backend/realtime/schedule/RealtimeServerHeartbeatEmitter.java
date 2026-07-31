package gg.modl.backend.realtime.schedule;

import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.realtime.dispatch.RealtimeDispatchExecutor;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import gg.modl.backend.realtime.transport.RealtimeCodec;
import gg.modl.backend.realtime.transport.RealtimeSessionOperations;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p>Delivery is handed to {@link RealtimeDispatchExecutor} per server rather than sent inline. A
 * WebSocket write to a peer that has stopped reading blocks until the container's blocking-send
 * timeout (~20s), so sending inline would let one wedged client delay every connection behind it in
 * the sweep — starving healthy clients of the very heartbeat that keeps their watchdog quiet. The
 * sharded executor keeps that stall inside the offending tenant and off the scheduler thread, which
 * is how outbound domain events are already delivered.</p>
 */
@Component
@RequiredArgsConstructor
public class RealtimeServerHeartbeatEmitter {
    private final RealtimeProperties properties;
    private final RealtimeConnectionRegistry connectionRegistry;
    private final RealtimeCodec codec;
    private final RealtimeSessionOperations sessionOperations;
    private final RealtimeDispatchExecutor dispatchExecutor;

    @Scheduled(fixedDelayString = "${modl.realtime.ws.server-heartbeat-interval-ms:25000}")
    public void emitHeartbeats() {
        if (!properties.isEnabled()) {
            return;
        }

        Map<String, List<RealtimeConnectionRegistry.RealtimeConnectionSnapshot>> byServer = new LinkedHashMap<>();
        for (RealtimeConnectionRegistry.RealtimeConnectionSnapshot snapshot : connectionRegistry.snapshot()) {
            String serverId = snapshot.state().getServerId();
            if (serverId == null || !isEligible(snapshot)) {
                continue;
            }
            byServer.computeIfAbsent(serverId, key -> new ArrayList<>()).add(snapshot);
        }

        byServer.forEach((serverId, snapshots) -> dispatchExecutor.execute(serverId, () -> sendAll(snapshots)));
    }

    private void sendAll(List<RealtimeConnectionRegistry.RealtimeConnectionSnapshot> snapshots) {
        for (RealtimeConnectionRegistry.RealtimeConnectionSnapshot snapshot : snapshots) {
            // Re-checked here because the connection may have closed between the sweep and this task
            // reaching the front of its worker queue.
            if (!isEligible(snapshot)) {
                continue;
            }
            RealtimeConnectionState state = snapshot.state();
            // Best effort: a failed keepalive is not itself grounds for tearing down the connection.
            // A genuinely dead peer stops sending client heartbeats and the sweeper closes it.
            sessionOperations.trySend(
                snapshot.session(), state, codec.heartbeat(state.nextOutboundHeartbeatSequence()));
        }
    }

    private boolean isEligible(RealtimeConnectionRegistry.RealtimeConnectionSnapshot snapshot) {
        RealtimeConnectionState state = snapshot.state();
        WebSocketSession session = snapshot.session();
        // Unauthenticated sessions are the handshake sweeper's business; sending to a closing or
        // terminal session would only race its close frame.
        return state.isAuthenticated()
            && !state.isClosing()
            && state.getTerminalSince() == null
            && session.isOpen();
    }
}
