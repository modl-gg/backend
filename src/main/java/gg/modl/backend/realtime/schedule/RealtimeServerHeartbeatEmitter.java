package gg.modl.backend.realtime.schedule;

import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import gg.modl.backend.realtime.transport.RealtimeCodec;
import gg.modl.backend.realtime.transport.RealtimeSessionOperations;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>Each connection is delivered on its own virtual thread. A WebSocket write to a peer that has
 * stopped reading blocks until the container's blocking-send timeout (~20s), so <em>any</em> shared
 * delivery thread lets one wedged peer delay the connections queued behind it past the very 75s
 * watchdog this component exists to satisfy — including a per-tenant sharded pool, where unrelated
 * servers collide on the same worker. Per-connection dispatch matches the granularity of the
 * per-connection send lock in {@link RealtimeSessionOperations}, so a wedged peer can only stall
 * itself. Virtual threads make that isolation cheap: a blocked socket write parks and unmounts its
 * carrier rather than occupying a pooled thread.</p>
 */
@Slf4j
@Component
public class RealtimeServerHeartbeatEmitter {
    private final RealtimeProperties properties;
    private final RealtimeConnectionRegistry connectionRegistry;
    private final RealtimeCodec codec;
    private final RealtimeSessionOperations sessionOperations;
    private final Executor deliveryExecutor;

    // Explicit: the package-private test constructor below makes the candidate set ambiguous, and
    // Spring falls back to a (non-existent) no-arg constructor unless one is annotated.
    @Autowired
    public RealtimeServerHeartbeatEmitter(
        RealtimeProperties properties,
        RealtimeConnectionRegistry connectionRegistry,
        RealtimeCodec codec,
        RealtimeSessionOperations sessionOperations
    ) {
        this(properties, connectionRegistry, codec, sessionOperations,
            Executors.newVirtualThreadPerTaskExecutor());
    }

    RealtimeServerHeartbeatEmitter(
        RealtimeProperties properties,
        RealtimeConnectionRegistry connectionRegistry,
        RealtimeCodec codec,
        RealtimeSessionOperations sessionOperations,
        Executor deliveryExecutor
    ) {
        this.properties = properties;
        this.connectionRegistry = connectionRegistry;
        this.codec = codec;
        this.sessionOperations = sessionOperations;
        this.deliveryExecutor = deliveryExecutor;
    }

    @Scheduled(fixedDelayString = "${modl.realtime.ws.server-heartbeat-interval-ms:25000}")
    public void emitHeartbeats() {
        if (!properties.isEnabled()) {
            return;
        }

        for (RealtimeConnectionRegistry.RealtimeConnectionSnapshot snapshot : connectionRegistry.snapshot()) {
            if (!isEligible(snapshot)) {
                continue;
            }
            deliveryExecutor.execute(() -> send(snapshot));
        }
    }

    private void send(RealtimeConnectionRegistry.RealtimeConnectionSnapshot snapshot) {
        // Re-checked here because the connection may have closed between the sweep and this task
        // being scheduled.
        if (!isEligible(snapshot)) {
            return;
        }
        RealtimeConnectionState state = snapshot.state();
        try {
            // Best effort: a failed keepalive is not itself grounds for tearing down the connection.
            // A genuinely dead peer stops sending client heartbeats and the sweeper closes it.
            sessionOperations.trySend(
                snapshot.session(), state, codec.heartbeat(state.nextOutboundHeartbeatSequence()));
        } catch (RuntimeException exception) {
            log.warn("Failed to send realtime heartbeat connection={}", state.getConnectionId(), exception);
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

    @PreDestroy
    public void shutdown() {
        if (deliveryExecutor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }
}
