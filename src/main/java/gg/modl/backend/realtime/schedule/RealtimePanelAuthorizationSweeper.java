package gg.modl.backend.realtime.schedule;

import gg.modl.backend.realtime.auth.RealtimeClientKind;
import gg.modl.backend.realtime.auth.RealtimePrincipal;
import gg.modl.backend.realtime.auth.RealtimeTopicAuthorizer;
import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.realtime.metrics.RealtimeMetrics;
import gg.modl.backend.realtime.state.RealtimeConnectionRegistry;
import gg.modl.backend.realtime.state.RealtimeConnectionState;
import gg.modl.proto.modl.v1.Topic;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimePanelAuthorizationSweeper {
    private static final String REVOCATION_PHASE = "resweep";

    private final RealtimeProperties properties;
    private final RealtimeConnectionRegistry connectionRegistry;
    private final RealtimeTopicAuthorizer topicAuthorizer;
    private final RealtimeMetrics metrics;

    @Scheduled(fixedDelayString = "${modl.realtime.ws.panel-authorization-sweep-interval-ms:30000}")
    public void revokeStalePanelSubscriptions() {
        if (!properties.isEnabled()) {
            return;
        }

        for (RealtimeConnectionRegistry.RealtimeConnectionSnapshot snapshot : connectionRegistry.snapshot()) {
            RealtimeConnectionState state = snapshot.state();
            if (!state.isAuthenticated() || state.getClientKind() != RealtimeClientKind.PANEL) {
                continue;
            }
            revokeStaleSubscriptions(state);
        }
    }

    private void revokeStaleSubscriptions(RealtimeConnectionState state) {
        RealtimePrincipal principal = state.getPrincipal();
        for (Topic topic : state.getSubscriptions()) {
            if (!topicAuthorizer.canSubscribe(principal, topic)) {
                state.unsubscribe(topic);
                metrics.recordTopicAuthorizationFailure(state, topic, REVOCATION_PHASE);
            }
        }
    }
}
