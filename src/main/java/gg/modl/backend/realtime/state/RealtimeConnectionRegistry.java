package gg.modl.backend.realtime.state;

import gg.modl.backend.realtime.config.RealtimeProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

@Component
@RequiredArgsConstructor
public class RealtimeConnectionRegistry {
    private static final String TERMINAL_ATTRIBUTE = "realtime.terminal";

    private final RealtimeProperties properties;
    private final ConcurrentMap<String, ConnectionEntry> connections = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, ConnectionEntry>> byServerId = new ConcurrentHashMap<>();

    public RealtimeConnectionState register(WebSocketSession session) {
        if (isTerminal(session)) {
            throw new IllegalStateException("Realtime session is terminal");
        }
        RealtimeConnectionState state = new RealtimeConnectionState();
        connections.put(session.getId(), new ConnectionEntry(decorate(session), state));
        return state;
    }

    public void onAuthenticated(WebSocketSession session) {
        ConnectionEntry entry = connections.get(session.getId());
        if (entry == null) {
            return;
        }
        String serverId = entry.state().getServerId();
        if (serverId == null) {
            return;
        }
        byServerId.computeIfAbsent(serverId, key -> new ConcurrentHashMap<>()).put(session.getId(), entry);
        if (connections.get(session.getId()) != entry) {
            pruneServerIndex(session.getId(), entry);
        }
    }

    public Optional<RealtimeConnectionState> get(WebSocketSession session) {
        return Optional.ofNullable(connections.get(session.getId()))
            .map(ConnectionEntry::state);
    }

    public Optional<RealtimeConnectionState> unregister(WebSocketSession session) {
        ConnectionEntry removed = connections.remove(session.getId());
        if (removed == null) {
            return Optional.empty();
        }
        pruneServerIndex(session.getId(), removed);
        return Optional.of(removed.state());
    }

    public Optional<WebSocketSession> getSession(WebSocketSession session) {
        return Optional.ofNullable(connections.get(session.getId()))
            .map(ConnectionEntry::session);
    }

    public void markTerminal(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        if (attributes != null) {
            attributes.put(TERMINAL_ATTRIBUTE, true);
        }
    }

    public boolean isTerminal(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        return attributes != null && Boolean.TRUE.equals(attributes.get(TERMINAL_ATTRIBUTE));
    }

    public List<RealtimeConnectionSnapshot> snapshot() {
        return connections.entrySet().stream()
            .map(entry -> toSnapshot(entry.getKey(), entry.getValue()))
            .toList();
    }

    public List<RealtimeConnectionSnapshot> snapshotByServer(String serverId) {
        ConcurrentMap<String, ConnectionEntry> serverConnections = byServerId.get(serverId);
        if (serverConnections == null) {
            return List.of();
        }
        return serverConnections.entrySet().stream()
            .map(entry -> toSnapshot(entry.getKey(), entry.getValue()))
            .toList();
    }

    private static RealtimeConnectionSnapshot toSnapshot(String sessionId, ConnectionEntry entry) {
        return new RealtimeConnectionSnapshot(sessionId, entry.session(), entry.state());
    }

    public void removeClosedSessions() {
        connections.forEach((sessionId, entry) -> {
            if (!entry.session().isOpen()) {
                connections.remove(sessionId, entry);
                pruneServerIndex(sessionId, entry);
            }
        });
    }

    private void pruneServerIndex(String sessionId, ConnectionEntry entry) {
        String serverId = entry.state().getServerId();
        if (serverId == null) {
            return;
        }
        byServerId.computeIfPresent(serverId, (key, inner) -> {
            inner.remove(sessionId, entry);
            return inner.isEmpty() ? null : inner;
        });
    }

    private WebSocketSession decorate(WebSocketSession session) {
        if (session instanceof ConcurrentWebSocketSessionDecorator) {
            return session;
        }
        return new ConcurrentWebSocketSessionDecorator(
            session,
            properties.getSendTimeLimitMs(),
            properties.getSendBufferSizeBytes(),
            ConcurrentWebSocketSessionDecorator.OverflowStrategy.TERMINATE
        );
    }

    private record ConnectionEntry(WebSocketSession session, RealtimeConnectionState state) {
    }

    public record RealtimeConnectionSnapshot(
        String sessionId,
        WebSocketSession session,
        RealtimeConnectionState state
    ) {
    }
}
