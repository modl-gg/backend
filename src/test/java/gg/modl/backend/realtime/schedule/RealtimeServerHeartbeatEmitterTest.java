package gg.modl.backend.realtime.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.realtime.auth.RealtimePrincipal;
import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.realtime.lifecycle.RealtimeConnectionCleanup;
import gg.modl.backend.realtime.metrics.RealtimeMetrics;
import gg.modl.backend.realtime.rate.RealtimeMessageRateLimiter;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import gg.modl.backend.realtime.transport.RealtimeCodec;
import gg.modl.backend.realtime.transport.RealtimeSessionOperations;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.proto.modl.v1.RealtimeEnvelope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Guards the server -> client liveness contract. The Minecraft plugin force-reconnects when it sees
 * no inbound frame for 75s, so the backend must emit unsolicited heartbeats on idle connections;
 * without them a healthy, fully subscribed connection is torn down roughly every 100 seconds.
 */
class RealtimeServerHeartbeatEmitterTest {

    @Test
    void sendsHeartbeatToAuthenticatedSession() throws Exception {
        Fixture fixture = new Fixture();
        WebSocketSession session = fixture.openSession("authenticated");
        RealtimeConnectionState state = fixture.registry.register(session);
        state.authenticate(RealtimePrincipal.minecraft(server("server-id")), 1);

        fixture.emitter.emitHeartbeats();

        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(session).sendMessage(captor.capture());
        RealtimeEnvelope envelope = RealtimeEnvelope.parseFrom(payload(captor.getValue()));
        assertEquals(RealtimeEnvelope.PayloadCase.HEARTBEAT, envelope.getPayloadCase());
    }

    /**
     * Both the plugin and the panel transport-ACK any frame carrying a non-empty event_id. An
     * event_id on a heartbeat would therefore double every keepalive into a request/response pair.
     */
    @Test
    void heartbeatCarriesNoEventIdSoClientsDoNotAckIt() throws Exception {
        Fixture fixture = new Fixture();
        WebSocketSession session = fixture.openSession("no-ack");
        RealtimeConnectionState state = fixture.registry.register(session);
        state.authenticate(RealtimePrincipal.minecraft(server("server-id")), 1);

        fixture.emitter.emitHeartbeats();

        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(session).sendMessage(captor.capture());
        RealtimeEnvelope envelope = RealtimeEnvelope.parseFrom(payload(captor.getValue()));
        assertTrue(envelope.getEventId().isEmpty(), "heartbeat must not carry an event_id");
        assertEquals(1, envelope.getProtocolVersion());
    }

    @Test
    void heartbeatSequenceAdvancesPerConnection() throws Exception {
        Fixture fixture = new Fixture();
        WebSocketSession session = fixture.openSession("sequenced");
        RealtimeConnectionState state = fixture.registry.register(session);
        state.authenticate(RealtimePrincipal.minecraft(server("server-id")), 1);

        fixture.emitter.emitHeartbeats();
        fixture.emitter.emitHeartbeats();

        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(session, times(2)).sendMessage(captor.capture());
        long first = RealtimeEnvelope.parseFrom(payload(captor.getAllValues().get(0))).getHeartbeat().getSequence();
        long second = RealtimeEnvelope.parseFrom(payload(captor.getAllValues().get(1))).getHeartbeat().getSequence();
        assertEquals(1L, first);
        assertEquals(2L, second);
    }

    @Test
    void doesNotHeartbeatUnauthenticatedSession() throws Exception {
        Fixture fixture = new Fixture();
        WebSocketSession session = fixture.openSession("unauthenticated");
        fixture.registry.register(session);

        fixture.emitter.emitHeartbeats();

        verify(session, never()).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void doesNotHeartbeatTerminalSession() throws Exception {
        Fixture fixture = new Fixture();
        WebSocketSession session = fixture.openSession("terminal");
        RealtimeConnectionState state = fixture.registry.register(session);
        state.authenticate(RealtimePrincipal.minecraft(server("server-id")), 1);
        state.markClosing();
        state.markTerminal();

        fixture.emitter.emitHeartbeats();

        verify(session, never()).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void doesNothingWhenRealtimeDisabled() throws Exception {
        Fixture fixture = new Fixture();
        fixture.properties.setEnabled(false);
        WebSocketSession session = fixture.openSession("disabled");
        RealtimeConnectionState state = fixture.registry.register(session);
        state.authenticate(RealtimePrincipal.minecraft(server("server-id")), 1);

        fixture.emitter.emitHeartbeats();

        verify(session, never()).sendMessage(any(BinaryMessage.class));
    }

    /**
     * A WebSocket write to a peer that stopped reading blocks for the container's blocking-send
     * timeout, so the sweep must never write on its own thread — one wedged client would otherwise
     * delay every connection behind it past the very 75s watchdog this component exists to satisfy.
     */
    @Test
    void doesNotWriteOnTheSweepThread() throws Exception {
        Fixture fixture = new Fixture(false);
        WebSocketSession session = fixture.openSession("deferred");
        RealtimeConnectionState state = fixture.registry.register(session);
        state.authenticate(RealtimePrincipal.minecraft(server("server-id")), 1);

        fixture.emitter.emitHeartbeats();

        verify(session, never()).sendMessage(any(BinaryMessage.class));
        assertEquals(1, fixture.deferred.size(), "heartbeat delivery must be handed to the executor");

        fixture.deferred.forEach(Runnable::run);
        verify(session).sendMessage(any(BinaryMessage.class));
    }

    /**
     * Delivery must be submitted per connection, not batched per server or per shard. Any coarser
     * grouping puts unrelated connections behind a single blocking write.
     */
    @Test
    void submitsOneTaskPerConnection() {
        Fixture fixture = new Fixture(false);
        WebSocketSession first = fixture.openSession("conn-a");
        fixture.registry.register(first).authenticate(RealtimePrincipal.minecraft(server("server-a")), 1);
        WebSocketSession second = fixture.openSession("conn-b");
        fixture.registry.register(second).authenticate(RealtimePrincipal.minecraft(server("server-a")), 1);
        WebSocketSession third = fixture.openSession("conn-c");
        fixture.registry.register(third).authenticate(RealtimePrincipal.minecraft(server("server-b")), 1);

        fixture.emitter.emitHeartbeats();

        assertEquals(3, fixture.deferred.size(), "each connection must be dispatched independently");
    }

    /**
     * End-to-end guard with a genuinely blocked socket write.
     *
     * <p>Both connections share one server, which is the worst case: it is what a per-tenant sharded
     * pool groups into a single sequential batch, and it subsumes the case of two different servers
     * colliding on the same shard. The wedged connection is placed first in the sweep order, and the
     * assertion runs while its write is still blocked — asserting after the sweep returns would pass
     * against a sequential implementation too, since by then the delayed send has completed.</p>
     */
    @Test
    void wedgedConnectionDoesNotDelayHeartbeatForAnother() throws Exception {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setEnabled(true);

        CountDownLatch releaseWedged = new CountDownLatch(1);
        CountDownLatch healthyReceived = new CountDownLatch(1);

        WebSocketSession wedged = openMockSession("wedged");
        doAnswer(invocation -> {
            releaseWedged.await(10, TimeUnit.SECONDS);
            return null;
        }).when(wedged).sendMessage(any(BinaryMessage.class));
        RealtimeConnectionState wedgedState = new RealtimeConnectionState();
        wedgedState.authenticate(RealtimePrincipal.minecraft(server("shared-server")), 1);

        WebSocketSession healthy = openMockSession("healthy");
        doAnswer(invocation -> {
            healthyReceived.countDown();
            return null;
        }).when(healthy).sendMessage(any(BinaryMessage.class));
        RealtimeConnectionState healthyState = new RealtimeConnectionState();
        healthyState.authenticate(RealtimePrincipal.minecraft(server("shared-server")), 1);

        // Fixed sweep order: wedged connection first, so a sequential implementation cannot pass.
        RealtimeConnectionRegistry registry = mock(RealtimeConnectionRegistry.class);
        when(registry.snapshot()).thenReturn(List.of(
            new RealtimeConnectionRegistry.RealtimeConnectionSnapshot("wedged", wedged, wedgedState),
            new RealtimeConnectionRegistry.RealtimeConnectionSnapshot("healthy", healthy, healthyState)));
        when(registry.getSession(any(WebSocketSession.class)))
            .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        when(registry.isTerminal(any(WebSocketSession.class))).thenReturn(false);

        RealtimeMetrics metrics = new RealtimeMetrics(new SimpleMeterRegistry());
        RealtimeConnectionCleanup cleanup =
            new RealtimeConnectionCleanup(registry, new RealtimeMessageRateLimiter(properties), metrics);
        ExecutorService deliveryExecutor = Executors.newVirtualThreadPerTaskExecutor();
        RealtimeServerHeartbeatEmitter emitter = new RealtimeServerHeartbeatEmitter(
            properties, registry, new RealtimeCodec(properties),
            new RealtimeSessionOperations(registry, cleanup, metrics), deliveryExecutor);

        Thread sweep = new Thread(emitter::emitHeartbeats, "heartbeat-sweep");
        sweep.setDaemon(true);
        try {
            sweep.start();

            assertTrue(
                healthyReceived.await(5, TimeUnit.SECONDS),
                "healthy connection must receive its heartbeat while another write is wedged");
        } finally {
            releaseWedged.countDown();
            sweep.join(TimeUnit.SECONDS.toMillis(15));
            deliveryExecutor.shutdownNow();
        }
    }

    private static final class Fixture {
        private final RealtimeProperties properties = new RealtimeProperties();
        private final RealtimeConnectionRegistry registry;
        private final List<Runnable> deferred = new ArrayList<>();
        private final RealtimeServerHeartbeatEmitter emitter;

        private Fixture() {
            this(true);
        }

        private Fixture(boolean runInline) {
            properties.setEnabled(true);
            registry = new RealtimeConnectionRegistry(properties);
            RealtimeMetrics metrics = new RealtimeMetrics(new SimpleMeterRegistry());
            RealtimeConnectionCleanup cleanup =
                new RealtimeConnectionCleanup(registry, new RealtimeMessageRateLimiter(properties), metrics);
            emitter = new RealtimeServerHeartbeatEmitter(
                properties,
                registry,
                new RealtimeCodec(properties),
                new RealtimeSessionOperations(registry, cleanup, metrics),
                runInline ? Runnable::run : deferred::add
            );
        }

        private WebSocketSession openSession(String id) {
            return openMockSession(id);
        }
    }

    private static WebSocketSession openMockSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        return session;
    }

    private static byte[] payload(BinaryMessage message) {
        byte[] payload = new byte[message.getPayloadLength()];
        message.getPayload().get(payload);
        return payload;
    }

    private static Server server(String id) {
        Server server = new Server("server", "server", "server_db", "admin@example.com", true, ServerPlan.FREE);
        server.setId(id);
        return server;
    }
}
