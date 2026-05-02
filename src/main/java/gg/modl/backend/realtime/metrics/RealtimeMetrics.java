package gg.modl.backend.realtime.metrics;

import gg.modl.backend.realtime.dispatch.RealtimeOutboundEvent;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import gg.modl.proto.modl.v1.Topic;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeMetrics {
    private final MeterRegistry meterRegistry;

    public void recordConnect(RealtimeConnectionState state) {
        increment("connect");
        log.info("Realtime websocket connected connectionId={}", state.getConnectionId());
    }

    public void recordDisconnect(RealtimeConnectionState state, CloseStatus status) {
        increment("disconnect");
        log.info(
            "Realtime websocket disconnected connectionId={} statusCode={} reason={}",
            state.getConnectionId(),
            status.getCode(),
            status.getReason()
        );
    }

    public void recordAuthFailure(String reason) {
        increment("auth_failure");
        log.warn("Realtime websocket auth failed reason={}", reason);
    }

    public void recordTopicAuthorizationFailure(RealtimeConnectionState state, Topic topic, String phase) {
        increment("topic_auth_failure", "phase", phase);
        log.warn(
            "Realtime topic authorization failed connectionId={} serverId={} topic={} phase={}",
            state.getConnectionId(),
            state.getServerId(),
            topic,
            phase
        );
    }

    public void recordReject(RealtimeConnectionState state, String reason) {
        increment("reject", "reason", reason);
        log.warn("Realtime websocket rejected connectionId={} reason={}", state.getConnectionId(), reason);
    }

    public void recordAck(RealtimeConnectionState state, String eventId) {
        increment("ack");
        log.debug("Realtime websocket ack connectionId={} eventId={}", state.getConnectionId(), eventId);
    }

    public void recordSendAttempt(RealtimeConnectionState state, RealtimeOutboundEvent event) {
        increment("send_attempt", "topic", event.topic().name());
        log.debug(
            "Realtime send attempt connectionId={} serverId={} topic={}",
            state.getConnectionId(),
            event.serverId(),
            event.topic()
        );
    }

    public void recordSendSuccess(RealtimeConnectionState state, RealtimeOutboundEvent event) {
        increment("send_success", "topic", event.topic().name());
        log.debug(
            "Realtime send success connectionId={} serverId={} topic={}",
            state.getConnectionId(),
            event.serverId(),
            event.topic()
        );
    }

    public void recordSendFailure(RealtimeConnectionState state, RealtimeOutboundEvent event, Throwable exception) {
        increment("send_failure", "topic", event.topic().name());
        log.warn(
            "Realtime send failed connectionId={} serverId={} topic={}",
            state.getConnectionId(),
            event.serverId(),
            event.topic(),
            exception
        );
    }

    public void recordInvalidOutboundPayload(RealtimeOutboundEvent event, Object payloadCase) {
        increment("invalid_outbound_payload", "topic", event.topic().name());
        log.warn(
            "Realtime outbound event rejected due to invalid topic/payload combination serverId={} topic={} payloadCase={}",
            event.serverId(),
            event.topic(),
            payloadCase
        );
    }

    public void recordTransportSendSuccess(RealtimeConnectionState state) {
        increment("send_success", "topic", "transport");
        log.debug("Realtime transport send success connectionId={}", state.getConnectionId());
    }

    public void recordTransportSendFailure(RealtimeConnectionState state, Throwable exception) {
        increment("send_failure", "topic", "transport");
        log.warn("Realtime transport send failed connectionId={}", state.getConnectionId(), exception);
    }

    public void recordTransportError(RealtimeConnectionState state, Throwable exception) {
        increment("transport_error");
        log.warn("Realtime websocket transport error connectionId={}", state.getConnectionId(), exception);
    }

    public void recordTimeoutClose(RealtimeConnectionState state) {
        increment("timeout_close");
        log.info("Realtime websocket heartbeat timed out connectionId={} serverId={}", state.getConnectionId(), state.getServerId());
    }

    public void recordReconnectClose(RealtimeConnectionState state, String reason) {
        increment("reconnect_close", "reason", reason);
        log.info("Realtime websocket closed for reconnect connectionId={} reason={}", state.getConnectionId(), reason);
    }

    private void increment(String event) {
        meterRegistry.counter("modl.realtime.events", "event", event).increment();
    }

    private void increment(String event, String tagKey, String tagValue) {
        meterRegistry.counter("modl.realtime.events", "event", event, tagKey, tagValue).increment();
    }
}
