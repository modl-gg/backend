package gg.modl.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.websocket.dto.SubscribeRequest;
import gg.modl.backend.websocket.dto.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModlWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;

    private final Map<String, Set<WebSocketSession>> serverSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToServer = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("WebSocket connection established: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            SubscribeRequest request = objectMapper.readValue(message.getPayload(), SubscribeRequest.class);

            if ("subscribe".equals(request.action()) && request.serverDomain() != null) {
                subscribeToServer(session, request.serverDomain());
                sendMessage(session, WebSocketMessage.of("subscribed", request.serverDomain(), Map.of("success", true)));
            } else if ("unsubscribe".equals(request.action())) {
                unsubscribeFromServer(session);
                sendMessage(session, WebSocketMessage.of("unsubscribed", null, Map.of("success", true)));
            } else if ("ping".equals(request.action())) {
                sendMessage(session, WebSocketMessage.of("pong", null, Map.of("timestamp", System.currentTimeMillis())));
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
            sendMessage(session, WebSocketMessage.of("error", null, Map.of("message", "Invalid message format")));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unsubscribeFromServer(session);
        log.debug("WebSocket connection closed: {} with status {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        unsubscribeFromServer(session);
    }

    private void subscribeToServer(WebSocketSession session, String serverDomain) {
        unsubscribeFromServer(session);

        serverSessions.computeIfAbsent(serverDomain, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionToServer.put(session.getId(), serverDomain);
        log.debug("Session {} subscribed to server {}", session.getId(), serverDomain);
    }

    private void unsubscribeFromServer(WebSocketSession session) {
        String serverDomain = sessionToServer.remove(session.getId());
        if (serverDomain != null) {
            Set<WebSocketSession> sessions = serverSessions.get(serverDomain);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    serverSessions.remove(serverDomain);
                }
            }
            log.debug("Session {} unsubscribed from server {}", session.getId(), serverDomain);
        }
    }

    public void broadcastToServer(String serverDomain, WebSocketMessage message) {
        Set<WebSocketSession> sessions = serverSessions.get(serverDomain);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                sendMessage(session, message);
            }
        }
    }

    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send WebSocket message to session {}: {}", session.getId(), e.getMessage());
        }
    }
}
