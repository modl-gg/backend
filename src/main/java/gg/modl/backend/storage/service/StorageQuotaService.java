package gg.modl.backend.storage.service;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.dto.response.StorageQuotaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageQuotaService {
    private final S3StorageService s3StorageService;

    private static final long FREE_TIER_BYTES = 1024L * 1024 * 1024; // 1 GB
    private static final long DEFAULT_PREMIUM_BYTES = 200L * 1024 * 1024 * 1024; // 200 GB
    public static final long MAX_PREMIUM_BYTES = 2200L * 1024L * 1024 * 1024; // 2200 GB (200 base + 2000 max overage)
    private static final long AI_FREE_LIMIT = 0L;
    private static final long AI_PREMIUM_LIMIT = 1000L;

    public StorageQuotaResponse getQuota(Server server) {
        Map<String, Long> byType = s3StorageService.calculateStorageByType(server);
        long usedBytes = byType.values().stream().mapToLong(Long::longValue).sum();
        long maxBytes = getMaxBytesForServer(server);

        double usedPercentage = maxBytes > 0 ? (double) usedBytes / maxBytes * 100 : 0;

        boolean isPremium = server.getPlan() == ServerPlan.PREMIUM;
        StorageQuotaResponse.AiQuotaInfo aiQuota = buildAiQuotaInfo(server, isPremium);

        return new StorageQuotaResponse(
                usedBytes,
                maxBytes,
                Math.round(usedPercentage * 100) / 100.0,
                formatBytes(usedBytes),
                formatBytes(maxBytes),
                byType,
                aiQuota,
                isPremium,
                0.08
        );
    }

    private StorageQuotaResponse.AiQuotaInfo buildAiQuotaInfo(Server server, boolean isPremium) {
        long baseLimit = isPremium ? AI_PREMIUM_LIMIT : AI_FREE_LIMIT;
        long totalUsed = 0;
        double usagePercentage = baseLimit > 0 ? (double) totalUsed / baseLimit * 100 : 0;

        return new StorageQuotaResponse.AiQuotaInfo(
                totalUsed,
                baseLimit,
                0L,
                0.0,
                isPremium,
                Math.round(usagePercentage * 100) / 100.0,
                Map.of(
                        "moderation", 0L,
                        "ticket_analysis", 0L,
                        "appeal_analysis", 0L,
                        "other", 0L
                )
        );
    }

    public boolean canUpload(Server server, long fileSize) {
        StorageQuotaResponse quota = getQuota(server);
        return quota.usedBytes() + fileSize <= quota.maxBytes();
    }

    private long getMaxBytesForServer(Server server) {
        if (server.getPlan() == ServerPlan.PREMIUM) {
            if (server.getMaxStorageLimitBytes() != null && server.getMaxStorageLimitBytes() > 0) {
                // Trust the database value directly â€” support can set values above MAX_PREMIUM_BYTES
                return server.getMaxStorageLimitBytes();
            }
            return DEFAULT_PREMIUM_BYTES;
        }
        return FREE_TIER_BYTES;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
