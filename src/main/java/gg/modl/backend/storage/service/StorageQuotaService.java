package gg.modl.backend.storage.service;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.response.StorageQuotaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageQuotaService {
    private final S3StorageService s3StorageService;

    private static final long FREE_TIER_BYTES = 100L * 1024 * 1024;
    private static final long PRO_TIER_BYTES = 1L * 1024 * 1024 * 1024;

    public StorageQuotaResponse getQuota(Server server) {
        long usedBytes = s3StorageService.calculateStorageUsed(server);
        long maxBytes = getMaxBytesForServer(server);

        double usedPercentage = maxBytes > 0 ? (double) usedBytes / maxBytes * 100 : 0;

        return new StorageQuotaResponse(
                usedBytes,
                maxBytes,
                Math.round(usedPercentage * 100) / 100.0,
                formatBytes(usedBytes),
                formatBytes(maxBytes)
        );
    }

    public boolean canUpload(Server server, long fileSize) {
        StorageQuotaResponse quota = getQuota(server);
        return quota.usedBytes() + fileSize <= quota.maxBytes();
    }

    private long getMaxBytesForServer(Server server) {
        return PRO_TIER_BYTES;
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
