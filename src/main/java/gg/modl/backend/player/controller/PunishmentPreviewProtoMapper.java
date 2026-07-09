package gg.modl.backend.player.controller;

import gg.modl.backend.player.dto.response.PunishmentPreviewView;
import gg.modl.backend.player.dto.response.PunishmentSeverityPreviewView;
import gg.modl.proto.modl.v1.PunishmentPreviewResponse;
import java.util.function.Consumer;

public final class PunishmentPreviewProtoMapper {
    private PunishmentPreviewProtoMapper() {
    }

    public static PunishmentPreviewResponse toProto(PunishmentPreviewView preview) {
        PunishmentPreviewResponse.Builder builder = PunishmentPreviewResponse.newBuilder()
            .setStatus(preview.getStatus())
            .setSuccess(preview.isSuccess())
            .setSingleSeverityPunishment(preview.isSingleSeverityPunishment())
            .setPermanentUntilUsernameChange(preview.isPermanentUntilUsernameChange())
            .setPermanentUntilSkinChange(preview.isPermanentUntilSkinChange())
            .setCanBeAltBlocking(preview.isCanBeAltBlocking())
            .setCanBeStatWiping(preview.isCanBeStatWiping())
            .setSocialPoints(preview.getSocialPoints())
            .setGameplayPoints(preview.getGameplayPoints());

        setIfNotNull(builder::setMessage, preview.getMessage());
        setIfNotNull(builder::setSocialStatus, preview.getSocialStatus());
        setIfNotNull(builder::setGameplayStatus, preview.getGameplayStatus());
        setIfNotNull(builder::setOffenderStatus, preview.getOffenderStatus());
        setIfNotNull(builder::setCategory, preview.getCategory());

        if (preview.getLenient() != null) {
            builder.setLenient(toProto(preview.getLenient()));
        }
        if (preview.getRegular() != null) {
            builder.setRegular(toProto(preview.getRegular()));
        }
        if (preview.getAggravated() != null) {
            builder.setAggravated(toProto(preview.getAggravated()));
        }
        if (preview.getSingleSeverity() != null) {
            builder.setSingleSeverity(toProto(preview.getSingleSeverity()));
        }

        return builder.build();
    }

    private static PunishmentPreviewResponse.SeverityPreview toProto(PunishmentSeverityPreviewView preview) {
        PunishmentPreviewResponse.SeverityPreview.Builder builder =
            PunishmentPreviewResponse.SeverityPreview.newBuilder()
                .setPermanent(preview.isPermanent())
                .setPoints(preview.getPoints())
                .setDurationMs(preview.getDurationMs())
                .setNewSocialPoints(preview.getNewSocialPoints())
                .setNewGameplayPoints(preview.getNewGameplayPoints());

        setIfNotNull(builder::setSeverity, preview.getSeverity());
        setIfNotNull(builder::setDurationFormatted, preview.getDurationFormatted());
        setIfNotNull(builder::setPunishmentType, preview.getPunishmentType());
        setIfNotNull(builder::setNewSocialStatus, preview.getNewSocialStatus());
        setIfNotNull(builder::setNewGameplayStatus, preview.getNewGameplayStatus());

        return builder.build();
    }

    private static void setIfNotNull(Consumer<String> setter, String value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
