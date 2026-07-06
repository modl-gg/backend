package gg.modl.backend.realtime.dispatch;

import gg.modl.backend.realtime.metrics.RealtimeMetrics;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import gg.modl.backend.realtime.transport.RealtimeCodec;
import gg.modl.backend.realtime.transport.RealtimeSessionOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class InProcessRealtimeEventDispatcher implements RealtimeEventDispatcher {
    private static final RealtimeDispatchResult ACCEPTED = new RealtimeDispatchResult(0, 0, 0);

    private final RealtimeConnectionRegistry connectionRegistry;
    private final RealtimeCodec codec;
    private final RealtimeMetrics metrics;
    private final RealtimeSessionOperations sessionOperations;
    private final RealtimeTopicPayloadValidator topicPayloadValidator;
    private final RealtimeDispatchExecutor dispatchExecutor;

    @Override
    public RealtimeDispatchResult publish(RealtimeOutboundEvent event) {
        dispatchExecutor.execute(event.serverId(), () -> dispatch(event));
        return ACCEPTED;
    }

    private void dispatch(RealtimeOutboundEvent event) {
        try {
            fanOut(event);
        } catch (RuntimeException exception) {
            log.warn("Failed to fan out realtime event topic={} server={}", event.topic(), event.serverId(), exception);
        }
    }

    private void fanOut(RealtimeOutboundEvent event) {
        if (!topicPayloadValidator.isValid(event)) {
            metrics.recordInvalidOutboundPayload(event, event.envelope().getPayloadCase());
            return;
        }

        byte[] payload = codec.outboundPayload(event.envelope());
        for (RealtimeConnectionRegistry.RealtimeConnectionSnapshot snapshot : connectionRegistry.snapshotByServer(event.serverId())) {
            RealtimeConnectionState state = snapshot.state();
            WebSocketSession session = snapshot.session();
            if (!matches(event, state, session)) {
                continue;
            }

            state.recordDeliveryAttempt();
            metrics.recordSendAttempt(state, event);
            if (sessionOperations.deliver(session, state, new BinaryMessage(payload))) {
                metrics.recordSendSuccess(state, event);
            } else {
                state.recordDeliveryFailure();
                metrics.recordSendFailure(state, event, new IllegalStateException("Realtime delivery failed"));
            }
        }
    }

    private boolean matches(RealtimeOutboundEvent event, RealtimeConnectionState state, WebSocketSession session) {
        return session.isOpen()
            && !state.isClosing()
            && !connectionRegistry.isTerminal(session)
            && state.isAuthenticated()
            && event.serverId().equals(state.getServerId())
            && state.isSubscribedTo(event.topic());
    }

}
