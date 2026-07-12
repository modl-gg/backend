package gg.modl.backend.database;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.TenantSchemaBootstrapper.BootstrapTarget;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.infrastructure.scheduling.SchedulerLeaseService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class TenantSchemaBootstrapperTest {
    @Test
    void bootstrapExistingTenantsAppliesIndexesToEachConfiguredServer() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        TenantMigrationService tenantMigrationService = mock(TenantMigrationService.class);
        MongoIndexReconciler reconciler = mock(MongoIndexReconciler.class);
        MongoTemplate globalTemplate = mock(MongoTemplate.class);
        MongoTemplate firstTemplate = mock(MongoTemplate.class);
        MongoTemplate secondTemplate = mock(MongoTemplate.class);

        BootstrapTarget configured = new BootstrapTarget("alpha", "server_alpha");
        BootstrapTarget alsoConfigured = new BootstrapTarget("beta", "server_beta");
        BootstrapTarget unprovisioned = new BootstrapTarget("gamma", null);
        when(tenantMongoAccess.global()).thenReturn(globalTemplate);
        when(globalTemplate.find(any(Query.class), eq(BootstrapTarget.class), eq(CollectionName.MODL_SERVERS)))
            .thenReturn(List.of(configured, alsoConfigured, unprovisioned));
        when(tenantMongoAccess.forDatabase("server_alpha")).thenReturn(firstTemplate);
        when(tenantMongoAccess.forDatabase("server_beta")).thenReturn(secondTemplate);

        TenantSchemaBootstrapper bootstrapper = new TenantSchemaBootstrapper(
            tenantMongoAccess, tenantMigrationService, reconciler, grantingLease());
        bootstrapper.bootstrapExistingTenants();

        verify(reconciler, timeout(5000)).createTenantIndexes(firstTemplate);
        verify(reconciler, timeout(5000)).createTenantIndexes(secondTemplate);
        verify(tenantMigrationService, timeout(5000)).applyMigrationsForTenant(firstTemplate);
        verify(tenantMigrationService, timeout(5000)).applyMigrationsForTenant(secondTemplate);
        verify(tenantMongoAccess, never()).forDatabase(null);
    }

    @Test
    void bootstrapExistingTenantsContinuesWhenIndividualTenantFails() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        TenantMigrationService tenantMigrationService = mock(TenantMigrationService.class);
        MongoIndexReconciler reconciler = mock(MongoIndexReconciler.class);
        MongoTemplate globalTemplate = mock(MongoTemplate.class);
        MongoTemplate goodTemplate = mock(MongoTemplate.class);

        BootstrapTarget broken = new BootstrapTarget("broken", "server_broken");
        BootstrapTarget healthy = new BootstrapTarget("healthy", "server_healthy");
        when(tenantMongoAccess.global()).thenReturn(globalTemplate);
        when(globalTemplate.find(any(Query.class), eq(BootstrapTarget.class), eq(CollectionName.MODL_SERVERS)))
            .thenReturn(List.of(broken, healthy));
        when(tenantMongoAccess.forDatabase("server_broken")).thenThrow(new IllegalStateException("boom"));
        when(tenantMongoAccess.forDatabase("server_healthy")).thenReturn(goodTemplate);

        TenantSchemaBootstrapper bootstrapper = new TenantSchemaBootstrapper(
            tenantMongoAccess, tenantMigrationService, reconciler, grantingLease());
        bootstrapper.bootstrapExistingTenants();

        verify(reconciler, timeout(5000)).createTenantIndexes(goodTemplate);
    }

    @Test
    void bootstrapExistingTenantsSkipsWhenLeaseHeldByAnotherInstance() {
        TenantMongoAccess tenantMongoAccess = mock(TenantMongoAccess.class);
        TenantMigrationService tenantMigrationService = mock(TenantMigrationService.class);
        MongoIndexReconciler reconciler = mock(MongoIndexReconciler.class);
        SchedulerLeaseService lease = mock(SchedulerLeaseService.class);
        when(lease.tryAcquire(any(), any())).thenReturn(false);

        TenantSchemaBootstrapper bootstrapper = new TenantSchemaBootstrapper(
            tenantMongoAccess, tenantMigrationService, reconciler, lease);
        bootstrapper.bootstrapExistingTenants();

        verifyNoInteractions(tenantMongoAccess);
        verifyNoInteractions(reconciler);
        verifyNoInteractions(tenantMigrationService);
    }

    private static SchedulerLeaseService grantingLease() {
        SchedulerLeaseService lease = mock(SchedulerLeaseService.class);
        when(lease.tryAcquire(any(), any())).thenReturn(true);
        return lease;
    }
}
