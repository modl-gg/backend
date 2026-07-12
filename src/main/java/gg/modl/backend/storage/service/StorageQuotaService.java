package gg.modl.backend.storage.service;

import gg.modl.backend.billing.service.UsageTrackingService;
import gg.modl.backend.limits.ServerLimitPolicy;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.dto.response.StorageQuotaResponse;
import gg.modl.backend.infrastructure.util.ByteFormatUtil;
import java.util.Map;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageQuotaService {
    private final StorageMetadataService storageMetadataService;
    private final UsageTrackingService usageTrackingService;
    private final StorageUsageAccountant storageUsageAccountant;
    private final StorageSyncService storageSyncService;
    private final ServerLimitPolicy serverLimitPolicy;

    private static final long BYTES_PER_GB = 1024L * 1024 * 1024;
    public static final long PREMIUM_BASE_BYTES = 200L * BYTES_PER_GB;
    public static final long MAX_STORAGE_OVERAGE_BYTES = 2000L * BYTES_PER_GB;
    public static final long MAX_PREMIUM_BYTES = PREMIUM_BASE_BYTES + MAX_STORAGE_OVERAGE_BYTES;
    public static final long MAX_AI_OVERAGE_REQUESTS = 5000L;
    private static final double AI_OVERAGE_RATE = 0.02;
    private static final double STORAGE_OVERAGE_RATE = 0.08;

    public enum ConfirmResult {
        SUCCESS,
        QUOTA_EXCEEDED,
        RECORD_FAILED
    }

    public boolean canUpload(Server server, long fileSize) {
        if (fileSize < 0) {
            return false;
        }
        long used = currentTrackedUsage(server);
        long max = getMaxBytesForServer(server);
        return used + fileSize <= max;
    }

    public boolean isWithinQuota(Server server) {
        long used = currentTrackedUsage(server);
        long max = getMaxBytesForServer(server);
        return used <= max;
    }

    private long currentTrackedUsage(Server server) {
        OptionalLong tracked = storageUsageAccountant.trackedBytes(server);
        if (tracked.isPresent()) {
            return tracked.getAsLong();
        }
        storageSyncService.triggerAsyncSync(server);
        return 0L;
    }

    public ConfirmResult confirmAndRecordFile(Server server, String key, long size, String contentType) {
        if (size < 0) {
            return ConfirmResult.RECORD_FAILED;
        }
        if (storageMetadataService.hasFile(server, key)) {
            return ConfirmResult.SUCCESS;
        }

        long maxBytes = getMaxBytesForServer(server);
        if (!storageUsageAccountant.tryReserveWithinLimit(server, size, maxBytes)) {
            return ConfirmResult.QUOTA_EXCEEDED;
        }

        StorageMetadataService.RecordFileResult recordResult =
            storageMetadataService.recordReservedFile(server, key, size, contentType);
        if (recordResult == StorageMetadataService.RecordFileResult.INSERTED) {
            return ConfirmResult.SUCCESS;
        }

        storageUsageAccountant.recordRemoval(server, size);
        return recordResult == StorageMetadataService.RecordFileResult.ALREADY_EXISTS
               ? ConfirmResult.SUCCESS
               : ConfirmResult.RECORD_FAILED;
    }

    public StorageQuotaResponse getQuota(Server server) {
        Map<String, Long> byType = storageMetadataService.calculateStorageByType(server);
        long usedBytes = byType.values()
            .stream().mapToLong(Long::longValue).sum();
        long maxBytes = getMaxBytesForServer(server);

        double usedPercentage = maxBytes > 0 ? (double) usedBytes / maxBytes * 100 : 0;

        boolean isPremium = server.getPlan() == ServerPlan.PREMIUM;
        StorageQuotaResponse.AiQuotaInfo aiQuota = buildAiQuotaInfo(server, isPremium);

        return new StorageQuotaResponse(
            usedBytes,
            maxBytes,
            Math.round(usedPercentage * 100) / 100.0,
            ByteFormatUtil.format(usedBytes),
            ByteFormatUtil.format(maxBytes),
            byType,
            aiQuota,
            isPremium,
            STORAGE_OVERAGE_RATE
        );
    }

    private StorageQuotaResponse.AiQuotaInfo buildAiQuotaInfo(Server server, boolean isPremium) {
        long totalUsed = server.getAiRequestsCurrentPeriod() != null ? server.getAiRequestsCurrentPeriod() : 0L;
        long includedLimit = usageTrackingService.getAiBaseLimitRequests();
        long requestLimit = usageTrackingService.getAiRequestLimit(server);
        boolean usageBillingEnabled = Boolean.TRUE.equals(server.getUsageBillingEnabled());
        long overageUsed = Math.max(0, totalUsed - includedLimit);
        double overageCost = usageBillingEnabled ? overageUsed * AI_OVERAGE_RATE : 0.0;
        double usagePercentage = requestLimit > 0 ? (double) totalUsed / requestLimit * 100 : 0;

        return new StorageQuotaResponse.AiQuotaInfo(
            totalUsed,
            requestLimit,
            overageUsed,
            overageCost,
            isPremium && totalUsed < requestLimit,
            Math.round(usagePercentage * 100) / 100.0,
            Map.of("other", totalUsed)
        );
    }

    private long getMaxBytesForServer(Server server) {
        return serverLimitPolicy.resolve(server).getMaxStorageBytes();
    }

}
