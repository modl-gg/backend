package gg.modl.backend.storage.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ServerUsageRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class StorageUsageAccountantTest {
    private static final String SERVER_ID = "srv-1";

    private ServerUsageRepository serverUsageRepository;
    private StorageUsageAccountant accountant;
    private Server server;

    @BeforeEach
    void setUp() {
        serverUsageRepository = mock(ServerUsageRepository.class);
        accountant = new StorageUsageAccountant(serverUsageRepository);
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        server.setId(SERVER_ID);
    }

    @Test
    void trackedBytesIsEmptyWhenCounterUnset() {
        server.setStorageUsedBytes(null);

        assertTrue(accountant.trackedBytes(server).isEmpty());
        assertFalse(accountant.isSynced(server));
        verifyNoInteractions(serverUsageRepository);
    }

    @Test
    void trackedBytesReflectsCounterWhenSet() {
        server.setStorageUsedBytes(4_096L);

        assertTrue(accountant.isSynced(server));
        assertTrue(accountant.trackedBytes(server).equals(OptionalLong.of(4_096L)));
    }

    @Test
    void recordAdditionDelegatesUnconditionalIncrement() {
        accountant.recordAddition(server, 512L);

        verify(serverUsageRepository).incrementStorageUsed(SERVER_ID, 512L);
    }

    @Test
    void tryReserveWithinLimitDelegatesAtomicGuardedIncrement() {
        when(serverUsageRepository.tryIncrementStorageUsedWithinLimit(SERVER_ID, 512L, 10_000L)).thenReturn(true);

        assertTrue(accountant.tryReserveWithinLimit(server, 512L, 10_000L));
        verify(serverUsageRepository).tryIncrementStorageUsedWithinLimit(SERVER_ID, 512L, 10_000L);
    }

    @Test
    void recordRemovalDelegatesFlooredDecrement() {
        accountant.recordRemoval(server, 512L);

        verify(serverUsageRepository).decrementStorageUsed(SERVER_ID, 512L);
    }

    @Test
    void setAuthoritativeUsageDelegatesAbsoluteSet() {
        accountant.setAuthoritativeUsage(server, 8_192L);

        verify(serverUsageRepository).setStorageUsed(SERVER_ID, 8_192L);
    }

    @Test
    void lowerUsageToActualDelegatesConditionalSet() {
        when(serverUsageRepository.setStorageUsedIfBelow(SERVER_ID, 8_192L)).thenReturn(true);

        assertTrue(accountant.lowerUsageToActual(server, 8_192L));
        verify(serverUsageRepository).setStorageUsedIfBelow(SERVER_ID, 8_192L);
    }

    @Test
    void reservationRollbackFiresDecrementAfterReserveWithMatchingBytes() {
        when(serverUsageRepository.tryIncrementStorageUsedWithinLimit(SERVER_ID, 512L, 10_000L)).thenReturn(true);

        accountant.tryReserveWithinLimit(server, 512L, 10_000L);
        accountant.recordRemoval(server, 512L);

        InOrder order = inOrder(serverUsageRepository);
        order.verify(serverUsageRepository).tryIncrementStorageUsedWithinLimit(SERVER_ID, 512L, 10_000L);
        order.verify(serverUsageRepository).decrementStorageUsed(SERVER_ID, 512L);
    }
}
