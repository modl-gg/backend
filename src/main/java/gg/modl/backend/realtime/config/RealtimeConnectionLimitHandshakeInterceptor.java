package gg.modl.backend.realtime.config;

import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.realtime.metrics.RealtimeMetrics;
import gg.modl.backend.realtime.rate.RealtimeUnauthenticatedConnectionLimiter;
import gg.modl.backend.realtime.rate.RealtimeUnauthenticatedConnectionLimiter.Admission;
import gg.modl.backend.realtime.transport.RealtimeSessionAttributes;
import gg.modl.backend.realtime.transport.RealtimeUnauthenticatedSlot;
import java.net.InetSocketAddress;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
@RequiredArgsConstructor
public class RealtimeConnectionLimitHandshakeInterceptor implements HandshakeInterceptor {
    private final RealtimeUnauthenticatedConnectionLimiter connectionLimiter;
    private final RealtimeMetrics metrics;

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        String clientIp = resolveClientIp(request);
        Admission admission = connectionLimiter.tryAcquire(clientIp);
        if (admission != Admission.ADMITTED) {
            metrics.recordUnauthenticatedConnectionRejected(rejectionScope(admission));
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }

        RealtimeUnauthenticatedSlot slot = new RealtimeUnauthenticatedSlot(clientIp);
        attributes.put(RealtimeSessionAttributes.UNAUTHENTICATED_SLOT, slot);
        stashOnRequest(request, slot);
        return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
        if (isUpgraded(response)) {
            return;
        }
        RealtimeUnauthenticatedSlot slot = slotFromRequest(request);
        if (slot != null) {
            slot.releaseOnce(connectionLimiter);
        }
    }

    private static boolean isUpgraded(ServerHttpResponse response) {
        return response instanceof ServletServerHttpResponse servletResponse
            && servletResponse.getServletResponse().getStatus() == HttpStatus.SWITCHING_PROTOCOLS.value();
    }

    private static void stashOnRequest(ServerHttpRequest request, RealtimeUnauthenticatedSlot slot) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            servletRequest.getServletRequest().setAttribute(RealtimeSessionAttributes.UNAUTHENTICATED_SLOT, slot);
        }
    }

    @Nullable
    private static RealtimeUnauthenticatedSlot slotFromRequest(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest
            && servletRequest.getServletRequest().getAttribute(RealtimeSessionAttributes.UNAUTHENTICATED_SLOT) instanceof RealtimeUnauthenticatedSlot slot) {
            return slot;
        }
        return null;
    }

    private static String rejectionScope(Admission admission) {
        return admission == Admission.REJECTED_PER_IP ? "per_ip" : "global";
    }

    @Nullable
    private static String resolveClientIp(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return RequestUtil.getClientIp(servletRequest.getServletRequest());
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress == null ? null : remoteAddress.getHostString();
    }
}
