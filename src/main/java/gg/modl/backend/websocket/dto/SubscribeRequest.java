package gg.modl.backend.websocket.dto;

public record SubscribeRequest(
        String action,
        String channel,
        String serverDomain
) {}
