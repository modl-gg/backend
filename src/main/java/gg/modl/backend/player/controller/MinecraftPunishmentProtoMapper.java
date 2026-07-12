package gg.modl.backend.player.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.dateAwareString;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalLong;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;

import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import gg.modl.backend.player.dto.request.MinecraftCreatePunishmentRequest;
import gg.modl.backend.player.dto.response.PunishmentView;
import gg.modl.proto.modl.v1.CreatePunishmentRequest;
import gg.modl.proto.modl.v1.PunishmentDetailResponse;
import gg.modl.proto.modl.v1.RecentPunishmentsResponse;

final class MinecraftPunishmentProtoMapper {
    private MinecraftPunishmentProtoMapper() {
    }

    static PunishmentDetailResponse.PunishmentDetailEntry toPunishmentDetail(PunishmentView punishment) {
        PunishmentDetailResponse.PunishmentDetailEntry.Builder builder =
            PunishmentDetailResponse.PunishmentDetailEntry.newBuilder()
                .setPlayerName(stringValue(punishment.playerName()))
                .setPlayerUuid(stringValue(punishment.playerUuid()))
                .setId(stringValue(punishment.id()))
                .setIssuerName(stringValue(punishment.issuerName()))
                .setIssued(dateAwareString(punishment.issued()))
                .setStarted(dateAwareString(punishment.started()))
                .setType(stringValue(punishment.type()))
                .setTypeOrdinal(punishment.typeOrdinal());

        punishment.attachedTicketIds().forEach(builder::addAttachedTicketIds);

        if (punishment.data() != null) {
            builder.setData(ProtoMapperSupport.legacyStruct(punishment.data()));
        }
        punishment.modifications().stream()
            .map(ProtoMapperSupport::legacyStruct)
            .forEach(builder::addModifications);
        punishment.notes().stream()
            .map(ProtoMapperSupport::legacyStruct)
            .forEach(builder::addNotes);
        punishment.evidence().stream()
            .map(ProtoMapperSupport::legacyStruct)
            .forEach(builder::addEvidence);

        return builder.build();
    }

    static RecentPunishmentsResponse.RecentPunishment toRecentPunishment(PunishmentView punishment) {
        RecentPunishmentsResponse.RecentPunishment.Builder builder =
            RecentPunishmentsResponse.RecentPunishment.newBuilder()
                .setPlayerName(stringValue(punishment.playerName()))
                .setPlayerUuid(stringValue(punishment.playerUuid()))
                .setId(stringValue(punishment.id()))
                .setIssuerName(stringValue(punishment.issuerName()))
                .setIssued(longValue(punishment.issued()))
                .setType(stringValue(punishment.type()));

        setOptionalLong(builder::setStarted, punishment.started());
        builder.setTypeOrdinal(punishment.typeOrdinal());
        punishment.modifications().stream()
            .map(MinecraftPlayerProtoMapper::toPunishmentModification)
            .forEach(builder::addModifications);
        punishment.notes().stream()
            .map(MinecraftPlayerProtoMapper::toPunishmentNote)
            .forEach(builder::addNotes);
        punishment.evidence().stream()
            .map(MinecraftPlayerProtoMapper::toPunishmentEvidence)
            .forEach(builder::addEvidence);
        punishment.attachedTicketIds().forEach(builder::addAttachedTicketIds);

        if (punishment.data() != null) {
            builder.setData(ProtoMapperSupport.legacyStruct(punishment.data()));
        }

        return builder.build();
    }

    static MinecraftCreatePunishmentRequest toLegacyCreatePunishmentRequest(
        CreatePunishmentRequest request
    ) {
        return new MinecraftCreatePunishmentRequest(
            request.getTargetUuid(),
            request.hasIssuerName() ? request.getIssuerName() : null,
            request.hasIssuerId() ? request.getIssuerId() : null,
            request.getTypeOrdinal(),
            request.hasReason() ? request.getReason() : null,
            request.hasDuration() ? request.getDuration() : null,
            request.hasData() ? ProtoMapperSupport.structToMap(request.getData()) : null,
            request.getNotesList(),
            request.getAttachedTicketIdsList(),
            request.hasSeverity() ? request.getSeverity() : null,
            request.hasStatus() ? request.getStatus() : null
        );
    }
}
