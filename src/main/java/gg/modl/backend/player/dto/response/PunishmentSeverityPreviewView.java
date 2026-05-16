package gg.modl.backend.player.dto.response;

public interface PunishmentSeverityPreviewView {
    String getSeverity();

    int getPoints();

    long getDurationMs();

    String getDurationFormatted();

    String getPunishmentType();

    boolean isPermanent();

    String getNewSocialStatus();

    String getNewGameplayStatus();

    int getNewSocialPoints();

    int getNewGameplayPoints();
}
