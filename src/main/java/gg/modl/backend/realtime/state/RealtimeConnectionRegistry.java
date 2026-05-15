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

    public RealtimeConnectionState register(WebSocketSession session) {
        if (isTerminal(session)) {
            throw new IllegalStateException("Realtime session is terminal");
        }
        RealtimeConnectionState state = new RealtimeConnectionState();
        connections.put(session.getId(), new ConnectionEntry(decorate(session), state));
        return state;
    }

    public Optional<RealtimeConnectionState> get(WebSocketSession session) {
        return Optional.ofNullable(connections.get(session.getId()))
            .map(ConnectionEntry::state);
    }

    public Optional<RealtimeConnectionState> unregister(WebSocketSession session) {
        return Optional.ofNullable(connections.remove(session.getId()))
            .map(ConnectionEntry::state);
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
            .map(entry -> new RealtimeConnectionSnapshot(entry.getKey(), entry.getValue().session(), entry.getValue().state()))
            .toList();
    }

    public void removeClosedSessions() {
        connections.entrySet().removeIf(entry -> !entry.getValue().session().isOpen());
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
