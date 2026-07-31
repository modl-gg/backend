package gg.modl.backend.realtime.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import java.util.concurrent.ConcurrentHashMap;
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
        state.authenticate(RealtimePrincipal.minecraft(server()), 1);

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
        state.authenticate(RealtimePrincipal.minecraft(server()), 1);

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
        state.authenticate(RealtimePrincipal.minecraft(server()), 1);

        fixture.emitter.emitHeartbeats();
        fixture.emitter.emitHeartbeats();

        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(session, org.mockito.Mockito.times(2)).sendMessage(captor.capture());
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
        state.authenticate(RealtimePrincipal.minecraft(server()), 1);
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
        state.authenticate(RealtimePrincipal.minecraft(server()), 1);

        fixture.emitter.emitHeartbeats();

        verify(session, never()).sendMessage(any(BinaryMessage.class));
    }

    private static final class Fixture {
        private final RealtimeProperties properties = new RealtimeProperties();
        private final RealtimeConnectionRegistry registry;
        private final RealtimeServerHeartbeatEmitter emitter;

        private Fixture() {
            properties.setEnabled(true);
            registry = new RealtimeConnectionRegistry(properties);
            RealtimeMetrics metrics = new RealtimeMetrics(new SimpleMeterRegistry());
            RealtimeConnectionCleanup cleanup =
                new RealtimeConnectionCleanup(registry, new RealtimeMessageRateLimiter(properties), metrics);
            emitter = new RealtimeServerHeartbeatEmitter(
                properties,
                registry,
                new RealtimeCodec(properties),
                new RealtimeSessionOperations(registry, cleanup, metrics)
            );
        }

        private WebSocketSession openSession(String id) {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn(id);
            when(session.isOpen()).thenReturn(true);
            when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
            return session;
        }
    }

    private static byte[] payload(BinaryMessage message) {
        byte[] payload = new byte[message.getPayloadLength()];
        message.getPayload().get(payload);
        return payload;
    }

    private static Server server() {
        Server server = new Server("server", "server", "server_db", "admin@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
        return server;
    }
}
