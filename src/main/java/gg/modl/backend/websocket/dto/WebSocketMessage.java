package gg.modl.backend.websocket.dto;

import java.util.Date;

public record WebSocketMessage(
        String type,
        String channel,
        Object data,
        Date timestamp
) {
    public static WebSocketMessage of(String type, String channel, Object data) {
        return new WebSocketMessage(type, channel, data, new Date());
    }
}
