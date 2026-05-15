package gg.modl.backend.player.dto.response;

public interface PunishmentPreviewView {
    int getStatus();

    boolean isSuccess();

    String getMessage();

    String getSocialStatus();

    String getGameplayStatus();

    int getSocialPoints();

    int getGameplayPoints();

    String getOffenderStatus();

    PunishmentSeverityPreviewView getLenient();

    PunishmentSeverityPreviewView getRegular();

    PunishmentSeverityPreviewView getAggravated();

    PunishmentSeverityPreviewView getSingleSeverity();

    boolean isSingleSeverityPunishment();

    boolean isPermanentUntilUsernameChange();

    boolean isPermanentUntilSkinChange();

    boolean isCanBeAltBlocking();

    boolean isCanBeStatWiping();

    String getCategory();
}
