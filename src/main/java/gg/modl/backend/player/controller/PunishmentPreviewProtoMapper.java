package gg.modl.backend.player.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalString;

import gg.modl.backend.player.dto.response.PunishmentPreviewView;
import gg.modl.backend.player.dto.response.PunishmentSeverityPreviewView;
import gg.modl.proto.modl.v1.PunishmentPreviewResponse;

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

        setOptionalString(builder::setMessage, preview.getMessage());
        setOptionalString(builder::setSocialStatus, preview.getSocialStatus());
        setOptionalString(builder::setGameplayStatus, preview.getGameplayStatus());
        setOptionalString(builder::setOffenderStatus, preview.getOffenderStatus());
        setOptionalString(builder::setCategory, preview.getCategory());

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

        setOptionalString(builder::setSeverity, preview.getSeverity());
        setOptionalString(builder::setDurationFormatted, preview.getDurationFormatted());
        setOptionalString(builder::setPunishmentType, preview.getPunishmentType());
        setOptionalString(builder::setNewSocialStatus, preview.getNewSocialStatus());
        setOptionalString(builder::setNewGameplayStatus, preview.getNewGameplayStatus());

        return builder.build();
    }
}
