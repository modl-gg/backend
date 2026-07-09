package gg.modl.backend.player.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.dateAwareString;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.intValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.list;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.listOfMaps;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalInt;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalLong;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringObjectMap;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;

import com.google.protobuf.Struct;
import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import gg.modl.proto.modl.v1.CreatePunishmentRequest;
import gg.modl.proto.modl.v1.PunishmentDetailResponse;
import gg.modl.proto.modl.v1.RecentPunishmentsResponse;
import java.util.Map;
import java.util.Objects;

final class MinecraftPunishmentProtoMapper {
    private MinecraftPunishmentProtoMapper() {
    }

    static PunishmentDetailResponse.PunishmentDetailEntry toPunishmentDetail(Map<String, Object> punishment) {
        PunishmentDetailResponse.PunishmentDetailEntry.Builder builder =
            PunishmentDetailResponse.PunishmentDetailEntry.newBuilder()
                .setPlayerName(stringValue(punishment.get("playerName")))
                .setPlayerUuid(stringValue(punishment.get("playerUuid")))
                .setId(stringValue(punishment.get("id")))
                .setIssuerName(stringValue(punishment.get("issuerName")))
                .setIssued(dateAwareString(punishment.get("issued")))
                .setStarted(dateAwareString(punishment.get("started")))
                .setType(stringValue(punishment.get("type")))
                .setTypeOrdinal(intValue(punishment.get("typeOrdinal")));

        list(punishment.get("attachedTicketIds")).stream()
            .map(Objects::toString)
            .forEach(builder::addAttachedTicketIds);

        Map<String, Object> data = mapValue(punishment.get("data"));
        if (data != null) {
            builder.setData(ProtoMapperSupport.legacyStruct(data));
        }
        listOfMaps(punishment.get("modifications")).stream()
            .map(ProtoMapperSupport::legacyStruct)
            .forEach(builder::addModifications);
        listOfMaps(punishment.get("notes")).stream()
            .map(ProtoMapperSupport::legacyStruct)
            .forEach(builder::addNotes);
        listOfMaps(punishment.get("evidence")).stream()
            .map(ProtoMapperSupport::legacyStruct)
            .forEach(builder::addEvidence);

        return builder.build();
    }

    static RecentPunishmentsResponse.RecentPunishment toRecentPunishment(Map<String, Object> punishment) {
        RecentPunishmentsResponse.RecentPunishment.Builder builder =
            RecentPunishmentsResponse.RecentPunishment.newBuilder()
                .setPlayerName(stringValue(punishment.get("playerName")))
                .setPlayerUuid(stringValue(punishment.get("playerUuid")))
                .setId(stringValue(punishment.get("id")))
                .setIssuerName(stringValue(punishment.get("issuerName")))
                .setIssued(longValue(punishment.get("issued")))
                .setType(stringValue(punishment.get("type")));

        setOptionalLong(builder::setStarted, punishment.get("started"));
        setOptionalInt(builder::setTypeOrdinal, punishment.get("typeOrdinal"));
        listOfMaps(punishment.get("modifications")).stream()
            .map(MinecraftPlayerProtoMapper::toPunishmentModification)
            .forEach(builder::addModifications);
        listOfMaps(punishment.get("notes")).stream()
            .map(MinecraftPlayerProtoMapper::toPunishmentNote)
            .forEach(builder::addNotes);
        listOfMaps(punishment.get("evidence")).stream()
            .map(MinecraftPlayerProtoMapper::toPunishmentEvidence)
            .forEach(builder::addEvidence);
        list(punishment.get("attachedTicketIds")).stream()
            .map(Objects::toString)
            .forEach(builder::addAttachedTicketIds);

        Map<String, Object> data = mapValue(punishment.get("data"));
        if (data != null) {
            builder.setData(ProtoMapperSupport.legacyStruct(data));
        }

        return builder.build();
    }

    static MinecraftPunishmentController.MinecraftCreatePunishmentRequest toLegacyCreatePunishmentRequest(
        CreatePunishmentRequest request
    ) {
        return new MinecraftPunishmentController.MinecraftCreatePunishmentRequest(
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

    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Struct struct) {
            return ProtoMapperSupport.structToMap(struct);
        }
        if (value instanceof Map<?, ?> map) {
            return stringObjectMap(map);
        }
        return null;
    }
}
