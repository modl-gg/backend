package gg.modl.backend.limits;

import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.service.StorageQuotaService;
import org.springframework.stereotype.Component;

@Component
public class DefaultServerLimitPolicy implements ServerLimitPolicy {
    private static final long BYTES_PER_GB = 1024L * 1024 * 1024;
    private static final double BYTES_PER_GB_DOUBLE = 1024.0 * 1024 * 1024;
    private static final long FREE_STORAGE_BYTES = BYTES_PER_GB;
    private static final long FREE_STAFF_SEATS = 5;
    private static final long PREMIUM_STAFF_SEATS = 100_000;
    private static final long DEFAULT_MIGRATION_FILE_SIZE_BYTES = 500L * 1024 * 1024;

    @Override
    public ServerLimits resolve(Server server) {
        if (isBetaTester(server)) {
            return betaLimits();
        }
        return standardLimits(server);
    }

    private boolean isBetaTester(Server server) {
        return Boolean.TRUE.equals(server.getBetaTester());
    }

    private ServerLimits betaLimits() {
        return ServerLimits.builder()
            .maxStaffSeats(BetaLimits.MAX_STAFF_SEATS)
            .maxStorageBytes(BetaLimits.MAX_STORAGE_BYTES)
            .aiModerationEnabled(BetaLimits.AI_MODERATION_ENABLED)
            .aiRequestLimit(BetaLimits.AI_REQUEST_LIMIT)
            .cdnLimitGb(BetaLimits.CDN_LIMIT_GB)
            .cdnOverageThresholdGb(BetaLimits.CDN_OVERAGE_THRESHOLD_GB)
            .customDomainAllowed(BetaLimits.CUSTOM_DOMAIN_ALLOWED)
            .migrationFileSizeLimit(BetaLimits.MIGRATION_FILE_SIZE_LIMIT)
            .maxUploadBytes(BetaLimits.MAX_UPLOAD_BYTES)
            .build();
    }

    private ServerLimits standardLimits(Server server) {
        boolean premium = server.getPlan() == ServerPlan.PREMIUM;
        long planBaseStorageBytes = premium ? StorageQuotaService.PREMIUM_BASE_BYTES : FREE_STORAGE_BYTES;
        long maxStorageBytes = premium && hasPositiveValue(server.getMaxStorageLimitBytes())
            ? server.getMaxStorageLimitBytes()
            : planBaseStorageBytes;

        return ServerLimits.builder()
            .maxStaffSeats(premium ? PREMIUM_STAFF_SEATS : FREE_STAFF_SEATS)
            .maxStorageBytes(maxStorageBytes)
            .aiModerationEnabled(premium)
            .aiRequestLimit(UsageTrackingService.AI_BASE_LIMIT_REQUESTS + aiOverageRequests(server))
            .cdnLimitGb(maxStorageBytes / BYTES_PER_GB_DOUBLE)
            .cdnOverageThresholdGb(planBaseStorageBytes / BYTES_PER_GB_DOUBLE)
            .customDomainAllowed(premium || Boolean.TRUE.equals(server.getCustomDomainGrandfathered()))
            .migrationFileSizeLimit(migrationFileSizeLimit(server))
            .maxUploadBytes(Long.MAX_VALUE)
            .build();
    }

    private boolean hasPositiveValue(Long value) {
        return value != null && value > 0;
    }

    private long aiOverageRequests(Server server) {
        Long overage = server.getMaxAiOverageRequests();
        return overage != null ? Math.max(0L, overage) : 0L;
    }

    private long migrationFileSizeLimit(Server server) {
        Long override = server.getMigrationFileSizeLimit();
        return override != null ? override : DEFAULT_MIGRATION_FILE_SIZE_BYTES;
    }
}
