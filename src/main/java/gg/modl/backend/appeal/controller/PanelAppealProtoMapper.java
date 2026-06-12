package gg.modl.backend.appeal.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.addAll;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.structToMap;

import com.google.protobuf.Value;
import gg.modl.backend.appeal.dto.request.AddAppealReplyRequest;
import gg.modl.backend.appeal.dto.request.UpdateAppealStatusRequest;
import gg.modl.backend.ticket.controller.PanelTicketProtoMapper;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.proto.modl.v1.AddTicketReplyResponse;
import gg.modl.proto.modl.v1.AppealTicketsResponse;
import java.util.List;

/**
 * Bridges the appeal domain to {@code modl.v1} proto for the panel appeal REST surface (proto-JSON).
 * Appeals reuse the ticket {@code TicketResponse} proto via {@link PanelTicketProtoMapper}; this mapper
 * adds the appeal list wrapper, the reply response, and inbound request reconstruction.
 */
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

    static gg.modl.backend.appeal.dto.request.CreateAppealRequest fromCreateAppealRequest(
        gg.modl.proto.modl.v1.CreateAppealRequest request
    ) {
        return new gg.modl.backend.appeal.dto.request.CreateAppealRequest(
            request.getPunishmentId(),
            request.getPlayerUuid(),
            request.getEmail(),
            request.hasReason() ? request.getReason() : null,
            request.hasEvidence() ? request.getEvidence() : null,
            request.hasAdditionalData() ? structToMap(request.getAdditionalData()) : null,
            valueListToObjects(request.getAttachmentsList()),
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
            valueListToObjects(request.getAttachmentsList())
        );
    }

    static UpdateAppealStatusRequest fromUpdateAppealStatusRequest(gg.modl.proto.modl.v1.UpdateAppealStatusRequest request) {
        return new UpdateAppealStatusRequest(
            request.getStatus(),
            request.hasLocked() ? request.getLocked() : null,
            request.hasStaffUsername() ? request.getStaffUsername() : null,
            request.hasResolution() ? request.getResolution() : null
        );
    }

    static List<Object> valueListToObjects(List<Value> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().map(PanelAppealProtoMapper::valueToObject).toList();
    }

    private static Object valueToObject(Value value) {
        return switch (value.getKindCase()) {
            case NULL_VALUE, KIND_NOT_SET -> null;
            case NUMBER_VALUE -> value.getNumberValue();
            case STRING_VALUE -> value.getStringValue();
            case BOOL_VALUE -> value.getBoolValue();
            case STRUCT_VALUE -> structToMap(value.getStructValue());
            case LIST_VALUE -> value.getListValue().getValuesList().stream()
                .map(PanelAppealProtoMapper::valueToObject)
                .toList();
        };
    }
}
