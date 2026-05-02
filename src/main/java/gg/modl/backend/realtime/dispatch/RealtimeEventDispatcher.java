package gg.modl.backend.realtime.dispatch;

public interface RealtimeEventDispatcher {
    RealtimeDispatchResult publish(RealtimeOutboundEvent event);
}
