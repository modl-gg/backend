package gg.modl.backend.settings.service;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

@Component
public class CustomDomainLockRegistry {
    private static final int STRIPES = 64;
    private final ReentrantLock[] locks = createLocks();

    public LockHold acquire(String... keys) {
        int[] stripeIndices = Arrays.stream(keys)
            .mapToInt(key -> Math.floorMod(key.hashCode(), STRIPES))
            .distinct()
            .sorted()
            .toArray();
        for (int stripeIndex : stripeIndices) {
            locks[stripeIndex].lock();
        }
        return new LockHold(stripeIndices);
    }

    private static ReentrantLock[] createLocks() {
        ReentrantLock[] created = new ReentrantLock[STRIPES];
        for (int i = 0; i < created.length; i++) {
            created[i] = new ReentrantLock();
        }
        return created;
    }

    public final class LockHold implements AutoCloseable {
        private final int[] stripeIndices;

        private LockHold(int[] stripeIndices) {
            this.stripeIndices = stripeIndices;
        }

        @Override
        public void close() {
            for (int i = stripeIndices.length - 1; i >= 0; i--) {
                locks[stripeIndices[i]].unlock();
            }
        }
    }
}
