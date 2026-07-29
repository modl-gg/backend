package gg.modl.backend.punishment.controller;

import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import gg.modl.backend.player.dto.response.AppealInfoView;
import gg.modl.proto.modl.v1.PublicPunishmentAppealInfoResponse;
import java.util.Date;

final class PublicPunishmentProtoMapper {

    private PublicPunishmentProtoMapper() {
    }

    static PublicPunishmentAppealInfoResponse toAppealInfo(AppealInfoView appealInfo) {
        PublicPunishmentAppealInfoResponse.Builder builder = PublicPunishmentAppealInfoResponse.newBuilder()
            .setId(ProtoMapperSupport.stringValue(appealInfo.id()))
            .setType(ProtoMapperSupport.stringValue(appealInfo.type()))
            .setActive(appealInfo.active())
            .setAppealable(appealInfo.appealable())
            .setPlayerUuid(ProtoMapperSupport.stringValue(appealInfo.playerUuid()));

        Long issued = epochMillis(appealInfo.issued());
        if (issued != null) {
            builder.setIssued(issued);
        }
        Long expires = epochMillis(appealInfo.expires());
        if (expires != null) {
            builder.setExpires(expires);
        }
        if (appealInfo.existingAppeal() != null) {
            builder.setExistingAppeal(ProtoMapperSupport.legacyStruct(appealInfo.existingAppeal()));
        }
        if (appealInfo.appealForm() != null) {
            builder.setAppealForm(ProtoMapperSupport.legacyStruct(appealInfo.appealForm()));
        }
        return builder.build();
    }

    private static Long epochMillis(Date value) {
        return value != null ? value.getTime() : null;
    }
}
