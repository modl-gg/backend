package gg.modl.backend.websocket;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.websocket.dto.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WebSocketMessageService {
    private final ModlWebSocketHandler webSocketHandler;

    public void notifyTicketCreated(Server server, Ticket ticket) {
        broadcastUpdate(server, "ticket:created", Map.of(
                "ticketId", ticket.getId(),
                "subject", ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
                "type", ticket.getType(),
                "status", ticket.getStatus(),
                "creator", ticket.getCreator()
        ));
    }

    public void notifyTicketUpdated(Server server, Ticket ticket) {
        broadcastUpdate(server, "ticket:updated", Map.of(
                "ticketId", ticket.getId(),
                "subject", ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
                "status", ticket.getStatus(),
                "locked", ticket.isLocked()
        ));
    }

    public void notifyTicketReply(Server server, String ticketId, String replierName) {
        broadcastUpdate(server, "ticket:reply", Map.of(
                "ticketId", ticketId,
                "replierName", replierName
        ));
    }

    public void notifyPunishmentCreated(Server server, String playerId, String punishmentType) {
        broadcastUpdate(server, "punishment:created", Map.of(
                "playerId", playerId,
                "type", punishmentType
        ));
    }

    public void notifyAppealCreated(Server server, String appealId, String punishmentId) {
        broadcastUpdate(server, "appeal:created", Map.of(
                "appealId", appealId,
                "punishmentId", punishmentId
        ));
    }

    public void notifyAppealStatusChanged(Server server, String appealId, String status) {
        broadcastUpdate(server, "appeal:status_changed", Map.of(
                "appealId", appealId,
                "status", status
        ));
    }

    public void notifyActivityUpdate(Server server, String activityType, String description) {
        broadcastUpdate(server, "activity:new", Map.of(
                "type", activityType,
                "description", description
        ));
    }

    private void broadcastUpdate(Server server, String type, Object data) {
        String channel = server.getCustomDomain();
        WebSocketMessage message = WebSocketMessage.of(type, channel, data);
        webSocketHandler.broadcastToServer(channel, message);
    }
}
