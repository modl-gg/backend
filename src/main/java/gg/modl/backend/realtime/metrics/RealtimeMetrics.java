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
    private static final String EVENTS_COUNTER = "modl.realtime.events";
    private static final String TRANSPORT_TOPIC = "transport";
    private static final String UNSET = "none";

    private final MeterRegistry meterRegistry;

    public void recordConnect() {
        increment("connect");
    }

    public void recordDisconnect(CloseStatus status) {
        incrementReason("disconnect", closeReason(status));
    }

    public void recordAuthFailure(String reason) {
        increment("auth_failure");
        log.warn("Realtime websocket auth failed reason={}", reason);
    }

    public void recordTopicAuthorizationFailure(RealtimeConnectionState state, Topic topic, String phase) {
        incrementPhase("topic_auth_failure", phase);
        log.warn(
            "Realtime topic authorization failed connectionId={} serverId={} topic={} phase={}",
            state.getConnectionId(),
            state.getServerId(),
            topic,
            phase
        );
    }

    public void recordReject(RealtimeConnectionState state, String reason) {
        incrementReason("reject", reason);
        log.warn("Realtime websocket rejected connectionId={} reason={}", state.getConnectionId(), reason);
    }

    public void recordUnauthenticatedConnectionRejected(String scope) {
        incrementReason("unauthenticated_connection_rejected", scope);
        log.warn("Realtime websocket rejected pre-auth connection scope={}", scope);
    }

    public void recordAck(RealtimeConnectionState state, String eventId) {
        increment("ack");
        log.debug("Realtime websocket ack connectionId={} eventId={}", state.getConnectionId(), eventId);
    }

    public void recordSendAttempt(RealtimeConnectionState state, RealtimeOutboundEvent event) {
        incrementTopic("send_attempt", event.topic().name());
        log.debug(
            "Realtime send attempt connectionId={} serverId={} topic={}",
            state.getConnectionId(),
            event.serverId(),
            event.topic()
        );
    }

    public void recordSendSuccess(RealtimeConnectionState state, RealtimeOutboundEvent event) {
        incrementTopic("send_success", event.topic().name());
        log.debug(
            "Realtime send success connectionId={} serverId={} topic={}",
            state.getConnectionId(),
            event.serverId(),
            event.topic()
        );
    }

    public void recordSendFailure(RealtimeConnectionState state, RealtimeOutboundEvent event, Throwable exception) {
        incrementTopic("send_failure", event.topic().name());
        log.warn(
            "Realtime send failed connectionId={} serverId={} topic={}",
            state.getConnectionId(),
            event.serverId(),
            event.topic(),
            exception
        );
    }

    public void recordInvalidOutboundPayload(RealtimeOutboundEvent event, Object payloadCase) {
        incrementTopic("invalid_outbound_payload", event.topic().name());
        log.warn(
            "Realtime outbound event rejected due to invalid topic/payload combination serverId={} topic={} payloadCase={}",
            event.serverId(),
            event.topic(),
            payloadCase
        );
    }

    public void recordTransportSendSuccess(RealtimeConnectionState state) {
        incrementTopic("send_success", TRANSPORT_TOPIC);
        log.debug("Realtime transport send success connectionId={}", state.getConnectionId());
    }

    public void recordTransportSendFailure(RealtimeConnectionState state, Throwable exception) {
        incrementTopic("send_failure", TRANSPORT_TOPIC);
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
        incrementReason("reconnect_close", reason);
        log.info("Realtime websocket closed for reconnect connectionId={} reason={}", state.getConnectionId(), reason);
    }

    private void increment(String event) {
        record(event, UNSET, UNSET, UNSET);
    }

    private void incrementTopic(String event, String topic) {
        record(event, topic, UNSET, UNSET);
    }

    private void incrementReason(String event, String reason) {
        record(event, UNSET, reason, UNSET);
    }

    private void incrementPhase(String event, String phase) {
        record(event, UNSET, UNSET, phase);
    }

    private void record(String event, String topic, String reason, String phase) {
        meterRegistry.counter(EVENTS_COUNTER, "event", event, "topic", topic, "reason", reason, "phase", phase).increment();
    }

    private static String closeReason(CloseStatus status) {
        if (status == null) {
            return UNSET;
        }
        return switch (status.getCode()) {
            case 1000 -> "normal";
            case 1001 -> "going_away";
            default -> "abnormal";
        };
    }
}
