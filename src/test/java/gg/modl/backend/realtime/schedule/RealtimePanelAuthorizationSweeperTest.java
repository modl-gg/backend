package gg.modl.backend.realtime.schedule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gg.modl.backend.realtime.auth.RealtimePrincipal;
import gg.modl.backend.realtime.auth.RealtimeTopicAuthorizer;
import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.realtime.metrics.RealtimeMetrics;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.proto.modl.v1.Topic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class RealtimePanelAuthorizationSweeperTest {

    @Test
    void revokesOnlyTopicsThatLostAuthorization() {
        RealtimeProperties properties = enabledProperties();
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(properties);
        RealtimeTopicAuthorizer authorizer = mock(RealtimeTopicAuthorizer.class);
        RealtimePanelAuthorizationSweeper sweeper = new RealtimePanelAuthorizationSweeper(
            properties, registry, authorizer, new RealtimeMetrics(new SimpleMeterRegistry()));

        RealtimePrincipal principal = RealtimePrincipal.panel(server(), "staff@example.com");
        RealtimeConnectionState state = registry.register(session("panel"));
        state.authenticate(principal, 1);
        state.subscribe(Topic.TOPIC_PANEL_TICKETS);
        state.subscribe(Topic.TOPIC_PANEL_DASHBOARD);

        when(authorizer.canSubscribe(principal, Topic.TOPIC_PANEL_TICKETS)).thenReturn(true);
        when(authorizer.canSubscribe(principal, Topic.TOPIC_PANEL_DASHBOARD)).thenReturn(false);

        sweeper.revokeStalePanelSubscriptions();

        assertTrue(state.isSubscribedTo(Topic.TOPIC_PANEL_TICKETS));
        assertFalse(state.isSubscribedTo(Topic.TOPIC_PANEL_DASHBOARD));
    }

    @Test
    void leavesMinecraftConnectionsUntouched() {
        RealtimeProperties properties = enabledProperties();
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(properties);
        RealtimeTopicAuthorizer authorizer = mock(RealtimeTopicAuthorizer.class);
        RealtimePanelAuthorizationSweeper sweeper = new RealtimePanelAuthorizationSweeper(
            properties, registry, authorizer, new RealtimeMetrics(new SimpleMeterRegistry()));

        RealtimeConnectionState state = registry.register(session("minecraft"));
        state.authenticate(RealtimePrincipal.minecraft(server(), "instance-1"), 1);
        state.subscribe(Topic.TOPIC_MINECRAFT_PUNISHMENTS);

        sweeper.revokeStalePanelSubscriptions();

        assertTrue(state.isSubscribedTo(Topic.TOPIC_MINECRAFT_PUNISHMENTS));
        verifyNoInteractions(authorizer);
    }

    @Test
    void doesNothingWhenDisabled() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setEnabled(false);
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(properties);
        RealtimeTopicAuthorizer authorizer = mock(RealtimeTopicAuthorizer.class);
        RealtimePanelAuthorizationSweeper sweeper = new RealtimePanelAuthorizationSweeper(
            properties, registry, authorizer, new RealtimeMetrics(new SimpleMeterRegistry()));

        RealtimeConnectionState state = registry.register(session("panel-disabled"));
        state.authenticate(RealtimePrincipal.panel(server(), "staff@example.com"), 1);
        state.subscribe(Topic.TOPIC_PANEL_TICKETS);

        sweeper.revokeStalePanelSubscriptions();

        assertTrue(state.isSubscribedTo(Topic.TOPIC_PANEL_TICKETS));
        verifyNoInteractions(authorizer);
    }

    @Test
    void skipsUnauthenticatedConnections() {
        RealtimeProperties properties = enabledProperties();
        RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(properties);
        RealtimeTopicAuthorizer authorizer = mock(RealtimeTopicAuthorizer.class);
        RealtimePanelAuthorizationSweeper sweeper = new RealtimePanelAuthorizationSweeper(
            properties, registry, authorizer, new RealtimeMetrics(new SimpleMeterRegistry()));

        registry.register(session("pending"));

        sweeper.revokeStalePanelSubscriptions();

        verifyNoInteractions(authorizer);
    }

    private RealtimeProperties enabledProperties() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setEnabled(true);
        return properties;
    }

    private Server server() {
        Server server = new Server("server", "server", "server_db", "admin@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
        return server;
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        return session;
    }
}
