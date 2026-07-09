package gg.modl.backend.realtime.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import gg.modl.backend.realtime.metrics.RealtimeMetrics;
import gg.modl.backend.realtime.rate.RealtimeUnauthenticatedConnectionLimiter;
import gg.modl.backend.realtime.transport.RealtimeSessionAttributes;
import gg.modl.backend.realtime.transport.RealtimeUnauthenticatedSlot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

class RealtimeConnectionLimitHandshakeInterceptorTest {

    @Test
    void admitsConnectionAndStashesSlot() {
        RealtimeProperties properties = new RealtimeProperties();
        RealtimeConnectionLimitHandshakeInterceptor interceptor = interceptor(properties);
        ServletServerHttpRequest request = request("1.2.3.4");
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean admitted = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes);

        assertTrue(admitted);
        assertTrue(attributes.get(RealtimeSessionAttributes.UNAUTHENTICATED_SLOT) instanceof RealtimeUnauthenticatedSlot);
        verify(response, never()).setStatusCode(any());
    }

    @Test
    void rejectsOverGlobalCapWithServiceUnavailable() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setMaxUnauthenticatedConnections(1);
        RealtimeConnectionLimitHandshakeInterceptor interceptor = interceptor(properties);
        WebSocketHandler handler = mock(WebSocketHandler.class);

        interceptor.beforeHandshake(request("1.1.1.1"), mock(ServerHttpResponse.class), handler, new HashMap<>());

        ServerHttpResponse response = mock(ServerHttpResponse.class);
        boolean admitted = interceptor.beforeHandshake(request("2.2.2.2"), response, handler, new HashMap<>());

        assertFalse(admitted);
        verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void nonUpgradedHandshakeReleasesSlotEvenWithoutException() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setMaxUnauthenticatedConnections(1);
        RealtimeConnectionLimitHandshakeInterceptor interceptor = interceptor(properties);
        WebSocketHandler handler = mock(WebSocketHandler.class);
        ServletServerHttpRequest request = request("1.1.1.1");

        assertTrue(interceptor.beforeHandshake(request, response(HttpStatus.OK.value()), handler, new HashMap<>()));
        interceptor.afterHandshake(request, response(HttpStatus.OK.value()), handler, null);

        assertTrue(interceptor.beforeHandshake(request("2.2.2.2"), response(HttpStatus.OK.value()), handler, new HashMap<>()));
    }

    @Test
    void upgradedHandshakeRetainsSlotForSessionLifecycle() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setMaxUnauthenticatedConnections(1);
        RealtimeConnectionLimitHandshakeInterceptor interceptor = interceptor(properties);
        WebSocketHandler handler = mock(WebSocketHandler.class);
        ServletServerHttpRequest request = request("1.1.1.1");

        assertTrue(interceptor.beforeHandshake(request, response(HttpStatus.OK.value()), handler, new HashMap<>()));
        interceptor.afterHandshake(request, response(HttpStatus.SWITCHING_PROTOCOLS.value()), handler, null);

        ServerHttpResponse response = mock(ServerHttpResponse.class);
        assertFalse(interceptor.beforeHandshake(request("2.2.2.2"), response, handler, new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void failedHandshakeReleasesSlot() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setMaxUnauthenticatedConnections(1);
        RealtimeConnectionLimitHandshakeInterceptor interceptor = interceptor(properties);
        WebSocketHandler handler = mock(WebSocketHandler.class);
        ServletServerHttpRequest request = request("1.1.1.1");

        assertTrue(interceptor.beforeHandshake(request, response(HttpStatus.OK.value()), handler, new HashMap<>()));
        interceptor.afterHandshake(request, response(HttpStatus.BAD_REQUEST.value()), handler, new IllegalStateException("upgrade failed"));

        assertTrue(interceptor.beforeHandshake(request("2.2.2.2"), response(HttpStatus.OK.value()), handler, new HashMap<>()));
    }

    private RealtimeConnectionLimitHandshakeInterceptor interceptor(RealtimeProperties properties) {
        return new RealtimeConnectionLimitHandshakeInterceptor(
            new RealtimeUnauthenticatedConnectionLimiter(properties),
            new RealtimeMetrics(new SimpleMeterRegistry()));
    }

    private ServletServerHttpRequest request(String remoteAddr) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr(remoteAddr);
        return new ServletServerHttpRequest(servletRequest);
    }

    private ServletServerHttpResponse response(int status) {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        servletResponse.setStatus(status);
        return new ServletServerHttpResponse(servletResponse);
    }
}
