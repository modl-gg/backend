package gg.modl.backend.infrastructure.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.SchedulerLeaseRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SchedulerLeaseServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-15T12:00:00Z");
    private static final String LEASE = "test-lease";
    private static final Duration TTL = Duration.ofMinutes(30);

    private SchedulerLeaseRepository repository;
    private SchedulerLeaseService service;

    @BeforeEach
    void setUp() {
        repository = mock(SchedulerLeaseRepository.class);
        service = new SchedulerLeaseService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void tryAcquireReturnsRepositoryResultWithComputedExpiry() {
        when(repository.tryAcquire(eq(LEASE), eq(NOW), eq(NOW.plus(TTL)), anyString())).thenReturn(true);

        assertTrue(service.tryAcquire(LEASE, TTL));
    }

    @Test
    void tryAcquireReturnsFalseWhenLeaseIsHeldElsewhere() {
        when(repository.tryAcquire(eq(LEASE), eq(NOW), eq(NOW.plus(TTL)), anyString())).thenReturn(false);

        assertFalse(service.tryAcquire(LEASE, TTL));
    }

    @Test
    void tryAcquireReturnsFalseWhenRepositoryThrows() {
        when(repository.tryAcquire(eq(LEASE), eq(NOW), eq(NOW.plus(TTL)), anyString()))
            .thenThrow(new RuntimeException("mongo unavailable"));

        assertFalse(service.tryAcquire(LEASE, TTL));
    }

    @Test
    void releaseDelegatesUsingTheOwnerThatAcquiredTheLease() {
        when(repository.tryAcquire(eq(LEASE), eq(NOW), eq(NOW.plus(TTL)), anyString())).thenReturn(true);

        service.tryAcquire(LEASE, TTL);
        service.release(LEASE);

        ArgumentCaptor<String> acquireOwner = ArgumentCaptor.forClass(String.class);
        verify(repository).tryAcquire(eq(LEASE), eq(NOW), eq(NOW.plus(TTL)), acquireOwner.capture());
        ArgumentCaptor<String> releaseOwner = ArgumentCaptor.forClass(String.class);
        verify(repository).release(eq(LEASE), releaseOwner.capture());

        assertEquals(acquireOwner.getValue(), releaseOwner.getValue());
    }
}
