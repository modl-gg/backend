package gg.modl.backend.ticket.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.addAll;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.structToMap;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toStruct;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.infrastructure.proto.PublicDataRedactor;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.request.SubmitTicketFormRequest;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.proto.modl.v1.CreateTicketResponse;
import gg.modl.proto.modl.v1.PublicTicketResponse;
import gg.modl.proto.modl.v1.PublicTicketStatusResponse;
import gg.modl.proto.modl.v1.SubmitPublicTicketResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PublicTicketProtoMapper {
    private PublicTicketProtoMapper() {
    }

    static CreateTicketResponse toCreateTicketResponse(TicketResponse ticket, String message) {
        return CreateTicketResponse.newBuilder()
            .setSuccess(true)
            .setTicketId(stringValue(ticket.id()))
            .setMessage(message)
            .setTicket(CreateTicketResponse.CreateTicketInfo.newBuilder()
                .setId(stringValue(ticket.id()))
                .setType(stringValue(ticket.type()))
                .setSubject(stringValue(ticket.subject()))
                .setStatus(stringValue(ticket.status()))
                .setCreated(ticket.date() != null ? ticket.date().toInstant().toString() : ""))
            .build();
    }

    static PublicTicketResponse toPublicTicketResponse(TicketResponse ticket, Ticket rawTicket, Set<String> formFieldAllowlist) {
        String creatorName = ticket.creatorName() != null ? ticket.creatorName() : "";
        List<gg.modl.proto.modl.v1.PublicTicketReply> publicReplies =
            (ticket.messages() == null ? List.<TicketReply>of() : ticket.messages()).stream()
                .map(PublicTicketProtoMapper::toPublicReply)
                .toList();

        PublicTicketResponse.Builder builder = PublicTicketResponse.newBuilder()
            .setId(stringValue(ticket.id()))
            .setIdAlias(stringValue(ticket.id()))
            .setType(stringValue(ticket.type()))
            .setSubject(stringValue(ticket.subject()))
            .setStatus(stringValue(ticket.status()))
            .setCreatorName(creatorName)
            .setCreator(creatorName)
            .setCreatorUuid("")
            .setReportedBy(ticket.reportedBy() != null ? ticket.reportedBy() : "")
            .setCategory(stringValue(ticket.category()))
            .setLocked(ticket.locked())
            .setReportedPlayer(ticket.reportedPlayer() != null ? ticket.reportedPlayer() : "")
            .setReportedPlayerUuid("")
            .setEmailAuthEnabled(ticket.emailAuthEnabled())
            .setData(PublicDataRedactor.toPublicStruct(ticket.data(), PublicDataRedactor.SYSTEM_DATA_ALLOWLIST))
            .setFormData(PublicDataRedactor.toPublicStruct(ticket.formData(), formFieldAllowlist));

        builder.addAllReplies(publicReplies);
        builder.addAllMessages(publicReplies);

        if (ticket.date() != null) {
            builder.setCreated(toTimestamp(ticket.date()));
            builder.setDate(toTimestamp(ticket.date()));
        }
        addAll(ticket.tags(), value -> value, builder::addTags);

        if (rawTicket.isEmailAuthEnabled() && ticket.chatMessages() != null) {
            ticket.chatMessages().forEach(message -> builder.addChatMessages(
                PublicTicketResponse.PublicTicketChatMessage.newBuilder()
                    .setContent(stringValue(message.getContent()))
                    .setTimestamp(toTimestamp(message.getTimestamp()))
                    .setSender(stringValue(message.getSender()))
                    .build()));
        }
        return builder.build();
    }

    static PublicTicketStatusResponse toStatusResponse(TicketResponse ticket) {
        PublicTicketStatusResponse.Builder builder = PublicTicketStatusResponse.newBuilder()
            .setId(stringValue(ticket.id()))
            .setType(stringValue(ticket.type()))
            .setSubject(stringValue(ticket.subject()))
            .setStatus(stringValue(ticket.status()))
            .setLocked(ticket.locked());
        if (ticket.date() != null) {
            builder.setCreated(toTimestamp(ticket.date()));
        }
        return builder.build();
    }

    static SubmitPublicTicketResponse toSubmitResponse(TicketResponse ticket) {
        return SubmitPublicTicketResponse.newBuilder()
            .setSuccess(true)
            .setMessage("Ticket submitted successfully")
            .setTicket(SubmitPublicTicketResponse.SubmitPublicTicketInfo.newBuilder()
                .setId(stringValue(ticket.id()))
                .setSubject(stringValue(ticket.subject()))
                .setStatus(stringValue(ticket.status())))
            .build();
    }

    static SubmitTicketFormRequest fromSubmitTicketFormRequest(gg.modl.proto.modl.v1.SubmitTicketFormRequest request) {
        return new SubmitTicketFormRequest(
            request.getSubject(),
            request.hasCreatorEmail() ? request.getCreatorEmail() : null,
            request.hasFormData() ? structToMap(request.getFormData()) : null,
            structListToObjects(request.getAttachmentsList()),
            request.hasCreatorIdentifier() ? request.getCreatorIdentifier() : null,
            request.getFieldLabelsMap().isEmpty() ? null : Map.copyOf(request.getFieldLabelsMap())
        );
    }

    static gg.modl.proto.modl.v1.PublicTicketReply toPublicReply(TicketReply reply) {
        gg.modl.proto.modl.v1.PublicTicketReply.Builder builder = gg.modl.proto.modl.v1.PublicTicketReply.newBuilder()
            .setId(stringValue(reply.getId()))
            .setName(stringValue(reply.getName()))
            .setAvatar(stringValue(reply.getAvatar()))
            .setContent(stringValue(reply.getContent()))
            .setType(stringValue(reply.getType()))
            .setStaff(reply.isStaff())
            .setAction(stringValue(reply.getAction()));
        if (reply.getCreated() != null) {
            builder.setCreated(toTimestamp(reply.getCreated()));
        }
        addAll(reply.getAttachments(), value -> toStruct(asMap(value)), builder::addAttachments);
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    static List<Object> attachmentsFromReply(gg.modl.proto.modl.v1.AddReplyRequest request) {
        return structListToObjects(request.getAttachmentsList());
    }

    private static List<Object> structListToObjects(List<com.google.protobuf.Struct> structs) {
        if (structs.isEmpty()) {
            return null;
        }
        return structs.stream()
            .map(struct -> (Object) structToMap(struct))
            .toList();
    }
}
