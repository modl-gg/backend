package gg.modl.backend.appeal.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.addAll;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.infrastructure.proto.PublicDataRedactor;
import gg.modl.backend.ticket.controller.PanelTicketProtoMapper;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.proto.modl.v1.AddPublicAppealReplyResponse;
import gg.modl.proto.modl.v1.CreatePublicAppealResponse;
import gg.modl.proto.modl.v1.PublicAppealResponse;
import gg.modl.proto.modl.v1.TicketVerificationRequiredResponse;
import java.util.List;

final class PublicAppealProtoMapper {
    private PublicAppealProtoMapper() {
    }

    static PublicAppealResponse toPublicAppealResponse(TicketResponse appeal) {
        String workflowStatus = appeal.appealWorkflowStatus() != null
            ? appeal.appealWorkflowStatus()
            : appeal.status();

        PublicAppealResponse.Builder builder = PublicAppealResponse.newBuilder()
            .setId(stringValue(appeal.id()))
            .setType(stringValue(appeal.type()))
            .setSubject(stringValue(appeal.subject()))
            .setStatus(stringValue(workflowStatus))
            .setAppealWorkflowStatus(stringValue(workflowStatus))
            .setCreatorName(stringValue(appeal.creatorName()))
            .setCreatorUuid("")
            .setLocked(appeal.locked())
            .setData(PublicDataRedactor.toPublicStruct(appeal.data(), PublicDataRedactor.SYSTEM_DATA_ALLOWLIST));

        if (appeal.date() != null) {
            builder.setCreated(toTimestamp(appeal.date()));
        }
        List<TicketReply> publicReplies = appeal.messages() == null ? List.of()
            : appeal.messages().stream()
                .filter(r -> !r.isStaff() && !"system".equalsIgnoreCase(r.getType()))
                .toList();
        addAll(publicReplies, PanelTicketProtoMapper::toTicketReply, builder::addMessages);
        addAll(appeal.tags(), value -> value, builder::addTags);
        return builder.build();
    }

    static CreatePublicAppealResponse toCreateAppealResponse(TicketResponse appeal) {
        String workflowStatus = appeal.appealWorkflowStatus() != null
            ? appeal.appealWorkflowStatus()
            : appeal.status();
        String created = appeal.date() != null ? appeal.date().toInstant().toString() : "";

        return CreatePublicAppealResponse.newBuilder()
            .setSuccess(true)
            .setAppealId(stringValue(appeal.id()))
            .setMessage("Appeal created successfully")
            .setAppeal(CreatePublicAppealResponse.CreatePublicAppealInfo.newBuilder()
                .setId(stringValue(appeal.id()))
                .setIdAlias(stringValue(appeal.id()))
                .setType(stringValue(appeal.type()))
                .setSubject(stringValue(appeal.subject()))
                .setStatus(stringValue(workflowStatus))
                .setAppealWorkflowStatus(stringValue(workflowStatus))
                .setCreated(created))
            .build();
    }

    static AddPublicAppealReplyResponse toAddReplyResponse(TicketReply reply) {
        return AddPublicAppealReplyResponse.newBuilder()
            .setSuccess(true)
            .setMessage("Reply added successfully")
            .setReply(PanelTicketProtoMapper.toPublicTicketReply(reply))
            .build();
    }

    static TicketVerificationRequiredResponse toVerificationRequiredResponse(String recordId, String emailHint) {
        return TicketVerificationRequiredResponse.newBuilder()
            .setRequiresVerification(true)
            .setEmailHint(stringValue(emailHint))
            .setTicketId(stringValue(recordId))
            .build();
    }
}
