package gg.modl.backend.punishment.controller;

import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import gg.modl.proto.modl.v1.PublicPunishmentAppealInfoResponse;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class PublicPunishmentProtoMapper {

    private PublicPunishmentProtoMapper() {
    }

    static PublicPunishmentAppealInfoResponse toAppealInfo(Map<String, Object> appealInfo) {
        PublicPunishmentAppealInfoResponse.Builder builder = PublicPunishmentAppealInfoResponse.newBuilder()
            .setId(string(appealInfo.get("id")))
            .setType(string(appealInfo.get("type")))
            .setActive(Boolean.TRUE.equals(appealInfo.get("active")))
            .setAppealable(Boolean.TRUE.equals(appealInfo.get("appealable")))
            .setPlayerUuid(string(appealInfo.get("playerUuid")));

        Long issued = epochMillis(appealInfo.get("issued"));
        if (issued != null) {
            builder.setIssued(issued);
        }
        Long expires = epochMillis(appealInfo.get("expires"));
        if (expires != null) {
            builder.setExpires(expires);
        }
        if (appealInfo.get("existingAppeal") instanceof Map<?, ?> existingAppeal) {
            builder.setExistingAppeal(ProtoMapperSupport.legacyStruct(normalizeDates(existingAppeal)));
        }
        if (appealInfo.get("appealForm") instanceof Map<?, ?> appealForm) {
            builder.setAppealForm(ProtoMapperSupport.legacyStruct(normalizeDates(appealForm)));
        }
        return builder.build();
    }

    private static Map<String, Object> normalizeDates(Map<?, ?> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> normalized.put(
            Objects.toString(key),
            value instanceof Date date ? date.getTime() : value
        ));
        return normalized;
    }

    private static Long epochMillis(Object value) {
        if (value instanceof Date date) {
            return date.getTime();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static String string(Object value) {
        return value == null ? "" : Objects.toString(value);
    }
}
