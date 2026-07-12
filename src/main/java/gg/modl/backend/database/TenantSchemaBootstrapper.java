package gg.modl.backend.database;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.infrastructure.scheduling.SchedulerLeaseService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSchemaBootstrapper {
    private static final int BOOTSTRAP_PARALLELISM = 4;
    private static final String TENANT_BOOTSTRAP_LEASE = "tenant-schema-bootstrap";
    private static final Duration TENANT_BOOTSTRAP_LEASE_TTL = Duration.ofMinutes(30);

    private final TenantMongoAccess tenantMongoAccess;
    private final TenantMigrationService tenantMigrationService;
    private final MongoIndexReconciler mongoIndexReconciler;
    private final SchedulerLeaseService schedulerLeaseService;

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapExistingTenants() {
        if (!schedulerLeaseService.tryAcquire(TENANT_BOOTSTRAP_LEASE, TENANT_BOOTSTRAP_LEASE_TTL)) {
            log.info("Skipping tenant schema bootstrap; lease held by another instance");
            return;
        }
        List<BootstrapTarget> targets;
        try {
            targets = loadBootstrapTargets();
        } catch (Exception e) {
            log.error("Failed to list servers for tenant bootstrap", e);
            return;
        }

        if (targets.isEmpty()) {
            log.info("Bootstrapping schema for 0 existing tenants");
            return;
        }

        dispatchTenantBootstrap(targets);
    }

    private List<BootstrapTarget> loadBootstrapTargets() {
        Query query = new Query();
        query.fields().include(ServerFields.ID).include(ServerFields.DATABASE_NAME);
        return tenantMongoAccess.global()
            .find(query, BootstrapTarget.class, CollectionName.MODL_SERVERS)
            .stream()
            .filter(target -> target.databaseName() != null && !target.databaseName().isBlank())
            .toList();
    }

    private void dispatchTenantBootstrap(List<BootstrapTarget> targets) {
        log.info("Bootstrapping schema for {} existing tenants", targets.size());
        int parallelism = Math.min(BOOTSTRAP_PARALLELISM, targets.size());
        ExecutorService executor = Executors.newFixedThreadPool(parallelism, bootstrapThreadFactory());
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        CompletableFuture<?>[] tasks = targets.stream()
            .map(target -> CompletableFuture.runAsync(() -> bootstrapTenant(target, succeeded, failed), executor))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(tasks).whenComplete((ignored, throwable) -> {
            log.info("Tenant schema bootstrap complete succeeded={} failed={}", succeeded.get(), failed.get());
            executor.shutdown();
        });
    }

    private void bootstrapTenant(BootstrapTarget target, AtomicInteger succeeded, AtomicInteger failed) {
        try {
            log.debug("Bootstrapping schema for server id={} database={}",
                target.id(), target.databaseName());
            MongoTemplate template = tenantMongoAccess.forDatabase(target.databaseName());
            tenantMigrationService.applyMigrationsForTenant(template);
            mongoIndexReconciler.createTenantIndexes(template);
            succeeded.incrementAndGet();
        } catch (Exception e) {
            failed.incrementAndGet();
            log.warn("Failed to bootstrap schema for server id={} database={}",
                target.id(), target.databaseName(), e);
        }
    }

    private ThreadFactory bootstrapThreadFactory() {
        AtomicInteger threadNumber = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "tenant-bootstrap-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    record BootstrapTarget(@Id String id, String databaseName) {
    }
}
