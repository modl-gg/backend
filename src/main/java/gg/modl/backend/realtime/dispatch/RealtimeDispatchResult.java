package gg.modl.backend.realtime.dispatch;

public record RealtimeDispatchResult(
    int matchedConnections,
    int deliveredConnections,
    int failedConnections
) {
}
