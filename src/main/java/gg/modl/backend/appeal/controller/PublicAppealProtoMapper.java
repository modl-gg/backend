package gg.modl.backend.appeal.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.addAll;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toStruct;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.ticket.controller.PanelTicketProtoMapper;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.proto.modl.v1.AddPublicAppealReplyResponse;
import gg.modl.proto.modl.v1.CreatePublicAppealResponse;
import gg.modl.proto.modl.v1.PublicAppealResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the appeal domain to the public appeal proto messages. Mirrors the redaction rules previously
 * applied by {@code PublicAppealResponse#fromTicketResponse}: contact/identity fields are stripped from
 * the free-form data and only the public-facing reply fields are exposed.
 */
final class PublicAppealProtoMapper {
    private static final Set<String> REDACTED_DATA_KEYS = Set.of(
        "contactEmail", "contact_email", "creatorEmail", "creatorIdentifier", "emailAuthEnabled", "email", "playerUuid");

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
            .setCreatorUuid(stringValue(appeal.creatorUuid()))
            .setLocked(appeal.locked())
            .setData(toStruct(filterPublicData(appeal.data())));

        if (appeal.date() != null) {
            builder.setCreated(toTimestamp(appeal.date()));
        }
        addAll(appeal.messages(), PanelTicketProtoMapper::toTicketReply, builder::addMessages);
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

    private static Map<String, Object> filterPublicData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> filtered = new HashMap<>(data);
        filtered.keySet().removeAll(REDACTED_DATA_KEYS);
        return filtered;
    }
}
