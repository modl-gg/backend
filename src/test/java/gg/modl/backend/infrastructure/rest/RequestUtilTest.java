package gg.modl.backend.infrastructure.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestUtilTest {

    @Test
    void getClientIpIgnoresForwardedHeadersByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.55, 10.0.0.4");
        request.addHeader("X-Real-IP", "203.0.113.56");

        assertEquals("198.51.100.10", RequestUtil.getClientIp(request));
    }

    @Test
    void firstValidIpSelectsRealClientByTrustedProxyCount() {
        assertEquals("2.2.2.2", RequestUtil.firstValidIp("1.1.1.1, 2.2.2.2", 1));
        assertEquals("1.1.1.1", RequestUtil.firstValidIp("1.1.1.1, 2.2.2.2", 2));
        assertEquals("9.9.9.9", RequestUtil.firstValidIp("9.9.9.9", 1));
        assertNull(RequestUtil.firstValidIp("1.1.1.1, 2.2.2.2", 3));
        assertNull(RequestUtil.firstValidIp(null, 1));
    }
}
