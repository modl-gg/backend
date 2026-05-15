package gg.modl.backend.minecraft;

import gg.modl.backend.player.dto.response.PunishmentPreviewResponse;

final class BackendPunishmentPreviewFactory {
    private BackendPunishmentPreviewFactory() {
    }

    static PunishmentPreviewResponse.PunishmentPreviewResponseBuilder previewBuilder() {
        return PunishmentPreviewResponse.builder();
    }

    static PunishmentPreviewResponse previewError(String message) {
        return PunishmentPreviewResponse.error(message);
    }

    static PunishmentPreviewResponse.SeverityPreview severityPreview(
        String severity,
        int points,
        long durationMs,
        String durationFormatted,
        String punishmentType,
        boolean permanent,
        String newSocialStatus,
        String newGameplayStatus,
        int newSocialPoints,
        int newGameplayPoints
    ) {
        return PunishmentPreviewResponse.SeverityPreview.builder()
            .severity(severity)
            .points(points)
            .durationMs(durationMs)
            .durationFormatted(durationFormatted)
            .punishmentType(punishmentType)
            .permanent(permanent)
            .newSocialStatus(newSocialStatus)
            .newGameplayStatus(newGameplayStatus)
            .newSocialPoints(newSocialPoints)
            .newGameplayPoints(newGameplayPoints)
            .build();
    }
}
