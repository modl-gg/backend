package gg.modl.backend.storage.dto.response;

public record StorageQuotaResponse(
        long usedBytes,
        long maxBytes,
        double usedPercentage,
        String usedFormatted,
        String maxFormatted
) {
}
