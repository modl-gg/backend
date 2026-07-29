package gg.modl.backend.appeal.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.addAll;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.structToMap;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.valuesToObjects;

import gg.modl.backend.appeal.dto.request.AddAppealReplyRequest;
import gg.modl.backend.appeal.dto.request.CreateAppealRequest;
import gg.modl.backend.appeal.dto.request.UpdateAppealStatusRequest;
import gg.modl.backend.ticket.controller.PanelTicketProtoMapper;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.proto.modl.v1.AddTicketReplyResponse;
import gg.modl.proto.modl.v1.AppealTicketsResponse;
import java.util.List;

final class PanelAppealProtoMapper {
    private PanelAppealProtoMapper() {
    }

    static AppealTicketsResponse toAppealTicketsResponse(List<TicketResponse> appeals) {
        AppealTicketsResponse.Builder builder = AppealTicketsResponse.newBuilder();
        addAll(appeals, PanelTicketProtoMapper::toTicketResponse, builder::addTickets);
        return builder.build();
    }

    static gg.modl.proto.modl.v1.TicketResponse toTicketResponse(TicketResponse appeal) {
        return PanelTicketProtoMapper.toTicketResponse(appeal);
    }

    static AddTicketReplyResponse toAddReplyResponse(TicketReply reply) {
        return AddTicketReplyResponse.newBuilder()
            .setSuccess(true)
            .setReply(PanelTicketProtoMapper.toPublicTicketReply(reply))
            .build();
    }

    static CreateAppealRequest fromCreateAppealRequest(
        gg.modl.proto.modl.v1.CreateAppealRequest request
    ) {
        return new CreateAppealRequest(
            request.getPunishmentId(),
            request.getPlayerUuid(),
            request.getEmail(),
            request.hasReason() ? request.getReason() : null,
            request.hasEvidence() ? request.getEvidence() : null,
            request.hasAdditionalData() ? structToMap(request.getAdditionalData()) : null,
            valuesToObjects(request.getAttachmentsList()),
            request.getFieldLabelsMap().isEmpty() ? null : java.util.Map.copyOf(request.getFieldLabelsMap())
        );
    }

    static AddAppealReplyRequest fromAddAppealReplyRequest(gg.modl.proto.modl.v1.AddAppealReplyRequest request) {
        return new AddAppealReplyRequest(
            request.getName(),
            request.getContent(),
            request.getType(),
            request.getStaff(),
            request.hasAction() ? request.getAction() : null,
            request.hasAvatar() ? request.getAvatar() : null,
            valuesToObjects(request.getAttachmentsList())
        );
    }

    static UpdateAppealStatusRequest fromUpdateAppealStatusRequest(gg.modl.proto.modl.v1.UpdateAppealStatusRequest request) {
        return new UpdateAppealStatusRequest(
            request.getStatus().isBlank() ? null : request.getStatus(),
            request.hasLocked() ? request.getLocked() : null,
            request.hasStaffUsername() ? request.getStaffUsername() : null,
            request.hasResolution() ? request.getResolution() : null
        );
    }

}
