package gg.modl.backend.realtime.rate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.modl.backend.realtime.config.RealtimeProperties;
import gg.modl.backend.realtime.rate.RealtimeUnauthenticatedConnectionLimiter.Admission;
import org.junit.jupiter.api.Test;

class RealtimeUnauthenticatedConnectionLimiterTest {

    @Test
    void globalCapRejectsBeyondLimitAndReleaseRestoresCapacity() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setMaxUnauthenticatedConnections(2);
        properties.setMaxUnauthenticatedConnectionsPerIp(100);
        RealtimeUnauthenticatedConnectionLimiter limiter = new RealtimeUnauthenticatedConnectionLimiter(properties);

        assertEquals(Admission.ADMITTED, limiter.tryAcquire("1.1.1.1"));
        assertEquals(Admission.ADMITTED, limiter.tryAcquire("2.2.2.2"));
        assertEquals(Admission.REJECTED_GLOBAL, limiter.tryAcquire("3.3.3.3"));

        limiter.release("1.1.1.1");
        assertEquals(Admission.ADMITTED, limiter.tryAcquire("3.3.3.3"));
    }

    @Test
    void perIpCapIsolatesSources() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setMaxUnauthenticatedConnections(100);
        properties.setMaxUnauthenticatedConnectionsPerIp(1);
        RealtimeUnauthenticatedConnectionLimiter limiter = new RealtimeUnauthenticatedConnectionLimiter(properties);

        assertEquals(Admission.ADMITTED, limiter.tryAcquire("1.1.1.1"));
        assertEquals(Admission.REJECTED_PER_IP, limiter.tryAcquire("1.1.1.1"));
        assertEquals(Admission.ADMITTED, limiter.tryAcquire("2.2.2.2"));

        limiter.release("1.1.1.1");
        assertEquals(Admission.ADMITTED, limiter.tryAcquire("1.1.1.1"));
    }

    @Test
    void releaseNeverDropsBelowZero() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setMaxUnauthenticatedConnections(1);
        properties.setMaxUnauthenticatedConnectionsPerIp(1);
        RealtimeUnauthenticatedConnectionLimiter limiter = new RealtimeUnauthenticatedConnectionLimiter(properties);

        limiter.release("1.1.1.1");
        limiter.release("1.1.1.1");

        assertEquals(Admission.ADMITTED, limiter.tryAcquire("1.1.1.1"));
        assertEquals(Admission.REJECTED_PER_IP, limiter.tryAcquire("1.1.1.1"));
    }

    @Test
    void nullClientIpIsTrackedUnderSharedBucket() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setMaxUnauthenticatedConnections(100);
        properties.setMaxUnauthenticatedConnectionsPerIp(1);
        RealtimeUnauthenticatedConnectionLimiter limiter = new RealtimeUnauthenticatedConnectionLimiter(properties);

        assertEquals(Admission.ADMITTED, limiter.tryAcquire(null));
        assertEquals(Admission.REJECTED_PER_IP, limiter.tryAcquire(null));

        limiter.release(null);
        assertEquals(Admission.ADMITTED, limiter.tryAcquire(null));
    }
}
