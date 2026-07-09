package gg.modl.backend.infrastructure.scheduling;

import gg.modl.backend.database.mongo.repository.SchedulerLeaseRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerLeaseService {
    private final SchedulerLeaseRepository repository;
    private final Clock clock;
    private final String owner = UUID.randomUUID().toString();

    public boolean tryAcquire(String leaseName, Duration ttl) {
        Instant now = clock.instant();
        try {
            return repository.tryAcquire(leaseName, now, now.plus(ttl), owner);
        } catch (Exception e) {
            log.warn("Failed to acquire scheduler lease {}; skipping run", leaseName, e);
            return false;
        }
    }

    public void release(String leaseName) {
        try {
            repository.release(leaseName, owner);
        } catch (Exception e) {
            log.warn("Failed to release scheduler lease {}", leaseName, e);
        }
    }
}
