package gg.modl.backend.realtime.dispatch;

import gg.modl.backend.realtime.metrics.RealtimeMetrics;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import gg.modl.backend.realtime.transport.RealtimeCodec;
import gg.modl.backend.realtime.transport.RealtimeSessionOperations;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class InProcessRealtimeEventDispatcher implements RealtimeEventDispatcher {
    private final RealtimeConnectionRegistry connectionRegistry;
    private final RealtimeCodec codec;
    private final RealtimeMetrics metrics;
    private final RealtimeSessionOperations sessionOperations;
    private final RealtimeTopicPayloadValidator topicPayloadValidator;

    @Override
    public RealtimeDispatchResult publish(RealtimeOutboundEvent event) {
        if (!topicPayloadValidator.isValid(event)) {
            metrics.recordInvalidOutboundPayload(event, event.envelope().getPayloadCase());
            return new RealtimeDispatchResult(0, 0, 0);
        }

        byte[] payload = codec.outboundPayload(event.envelope());
        int matched = 0;
        int delivered = 0;
        int failed = 0;

        for (RealtimeConnectionRegistry.RealtimeConnectionSnapshot snapshot : connectionRegistry.snapshot()) {
            RealtimeConnectionState state = snapshot.state();
            WebSocketSession session = snapshot.session();
            if (!matches(event, state, session)) {
                continue;
            }

            matched++;
            state.recordDeliveryAttempt();
            metrics.recordSendAttempt(state, event);
            if (sessionOperations.deliver(session, state, new BinaryMessage(Arrays.copyOf(payload, payload.length)))) {
                delivered++;
                metrics.recordSendSuccess(state, event);
            } else {
                failed++;
                state.recordDeliveryFailure();
                metrics.recordSendFailure(state, event, new IllegalStateException("Realtime delivery failed"));
            }
        }

        return new RealtimeDispatchResult(matched, delivered, failed);
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
