package gg.modl.backend.storage.dto.response;

import java.util.Map;

public record StorageQuotaResponse(
    long usedBytes,
    long maxBytes,
    double usedPercentage,
    String usedFormatted,
    String maxFormatted,
    Map<String, Long> byType,
    AiQuotaInfo aiQuota,
    boolean isPremium,
    double storageOverageRate
) {
    public record AiQuotaInfo(
        long totalUsed,
        long baseLimit,
        long overageUsed,
        double overageCost,
        boolean canUseAI,
        double usagePercentage,
        Map<String, Long> byService
    ) {}
}
