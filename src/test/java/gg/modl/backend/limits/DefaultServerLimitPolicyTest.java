package gg.modl.backend.limits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import org.junit.jupiter.api.Test;

class DefaultServerLimitPolicyTest {
    private static final long GB = 1024L * 1024 * 1024;
    private static final long MB = 1024L * 1024;
    private static final long FREE_STORAGE_BYTES = GB;
    private static final long PREMIUM_DEFAULT_STORAGE_BYTES = 200L * GB;
    private static final long DEFAULT_MIGRATION_BYTES = 500L * MB;
    private static final long AI_BASE_REQUESTS = 1000L;

    private final DefaultServerLimitPolicy policy = new DefaultServerLimitPolicy();

    private Server server(ServerPlan plan) {
        return new Server("server", "tenant", "server_tenant", "admin@example.com", true, plan);
    }

    @Test
    void freeServerResolvesLegacyFreeLimits() {
        ServerLimits limits = policy.resolve(server(ServerPlan.FREE));

        assertEquals(5, limits.getMaxStaffSeats());
        assertEquals(FREE_STORAGE_BYTES, limits.getMaxStorageBytes());
        assertFalse(limits.isAiModerationEnabled());
        assertEquals(AI_BASE_REQUESTS, limits.getAiRequestLimit());
        assertFalse(limits.isCustomDomainAllowed());
        assertEquals(DEFAULT_MIGRATION_BYTES, limits.getMigrationFileSizeLimit());
        assertEquals(Long.MAX_VALUE, limits.getMaxUploadBytes());
    }

    @Test
    void premiumServerResolvesLegacyPremiumLimits() {
        ServerLimits limits = policy.resolve(server(ServerPlan.PREMIUM));

        assertEquals(100_000, limits.getMaxStaffSeats());
        assertEquals(PREMIUM_DEFAULT_STORAGE_BYTES, limits.getMaxStorageBytes());
        assertTrue(limits.isAiModerationEnabled());
        assertEquals(AI_BASE_REQUESTS, limits.getAiRequestLimit());
        assertTrue(limits.isCustomDomainAllowed());
        assertEquals(DEFAULT_MIGRATION_BYTES, limits.getMigrationFileSizeLimit());
        assertEquals(Long.MAX_VALUE, limits.getMaxUploadBytes());
    }

    @Test
    void premiumGrandfatheredServerAllowsCustomDomain() {
        Server server = server(ServerPlan.PREMIUM);
        server.setCustomDomainGrandfathered(true);

        assertTrue(policy.resolve(server).isCustomDomainAllowed());
    }

    @Test
    void freeGrandfatheredServerAllowsCustomDomain() {
        Server server = server(ServerPlan.FREE);
        server.setCustomDomainGrandfathered(true);

        assertTrue(policy.resolve(server).isCustomDomainAllowed());
    }

    @Test
    void premiumHonorsStorageAndAiOverrides() {
        Server server = server(ServerPlan.PREMIUM);
        server.setMaxStorageLimitBytes(500L * GB);
        server.setMaxAiOverageRequests(2000L);

        ServerLimits limits = policy.resolve(server);

        assertEquals(500L * GB, limits.getMaxStorageBytes());
        assertEquals(AI_BASE_REQUESTS + 2000L, limits.getAiRequestLimit());
    }

    @Test
    void freeServerIgnoresStoragePremiumOverride() {
        Server server = server(ServerPlan.FREE);
        server.setMaxStorageLimitBytes(500L * GB);

        ServerLimits limits = policy.resolve(server);

        assertEquals(FREE_STORAGE_BYTES, limits.getMaxStorageBytes());
    }

    @Test
    void migrationOverrideHonoredRegardlessOfPlan() {
        long override = 123_456L;

        Server free = server(ServerPlan.FREE);
        free.setMigrationFileSizeLimit(override);
        assertEquals(override, policy.resolve(free).getMigrationFileSizeLimit());

        Server premium = server(ServerPlan.PREMIUM);
        premium.setMigrationFileSizeLimit(override);
        assertEquals(override, policy.resolve(premium).getMigrationFileSizeLimit());
    }

    @Test
    void migrationDefaultsTo500MbRegardlessOfPlan() {
        assertEquals(DEFAULT_MIGRATION_BYTES, policy.resolve(server(ServerPlan.FREE)).getMigrationFileSizeLimit());
        assertEquals(DEFAULT_MIGRATION_BYTES, policy.resolve(server(ServerPlan.PREMIUM)).getMigrationFileSizeLimit());
    }

    @Test
    void betaTesterResolvesStrictCapsAndIgnoresOverrides() {
        Server server = server(ServerPlan.PREMIUM);
        server.setBetaTester(true);
        server.setCustomDomainGrandfathered(true);
        server.setMaxStorageLimitBytes(500L * GB);
        server.setMaxAiOverageRequests(2000L);
        server.setMigrationFileSizeLimit(800L * MB);

        ServerLimits limits = policy.resolve(server);

        assertEquals(3, limits.getMaxStaffSeats());
        assertEquals(100L * MB, limits.getMaxStorageBytes());
        assertTrue(limits.isAiModerationEnabled());
        assertEquals(50, limits.getAiRequestLimit());
        assertFalse(limits.isCustomDomainAllowed());
        assertEquals(0, limits.getMigrationFileSizeLimit());
        assertEquals(5L * MB, limits.getMaxUploadBytes());
    }

    @Test
    void betaTesterCapsApplyEvenOnFreePlan() {
        Server server = server(ServerPlan.FREE);
        server.setBetaTester(true);

        ServerLimits limits = policy.resolve(server);

        assertEquals(3, limits.getMaxStaffSeats());
        assertEquals(100L * MB, limits.getMaxStorageBytes());
        assertTrue(limits.isAiModerationEnabled());
        assertEquals(50, limits.getAiRequestLimit());
        assertFalse(limits.isCustomDomainAllowed());
        assertEquals(5L * MB, limits.getMaxUploadBytes());
    }

    @Test
    void nullBetaTesterIsTreatedAsNonBeta() {
        ServerLimits limits = policy.resolve(server(ServerPlan.PREMIUM));

        assertEquals(100_000, limits.getMaxStaffSeats());
        assertEquals(Long.MAX_VALUE, limits.getMaxUploadBytes());
    }

    @Test
    void exceedsUploadLimitGuardsBetaButNotStandard() {
        ServerLimits standard = policy.resolve(server(ServerPlan.PREMIUM));
        assertFalse(standard.exceedsUploadLimit(Long.MAX_VALUE));

        Server betaServer = server(ServerPlan.PREMIUM);
        betaServer.setBetaTester(true);
        ServerLimits beta = policy.resolve(betaServer);
        assertTrue(beta.exceedsUploadLimit(5L * MB + 1));
        assertFalse(beta.exceedsUploadLimit(5L * MB));
    }
}
