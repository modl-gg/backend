package gg.modl.backend.ticket.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.addAll;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.structToMap;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toStruct;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.request.SubmitTicketFormRequest;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.proto.modl.v1.CreateTicketResponse;
import gg.modl.proto.modl.v1.PublicTicketResponse;
import gg.modl.proto.modl.v1.PublicTicketStatusResponse;
import gg.modl.proto.modl.v1.PublicTicketVerificationRequestResponse;
import gg.modl.proto.modl.v1.PublicTicketVerificationResponse;
import gg.modl.proto.modl.v1.SubmitPublicTicketResponse;
import gg.modl.proto.modl.v1.TicketVerificationRequiredResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the ticket domain to the public ticket proto messages. Carries the redaction rules the public
 * REST surface previously applied inline in {@code PublicTicketController}: contact/identity data is
 * stripped and only public-facing reply fields are exposed.
 */
final class PublicTicketProtoMapper {
    private static final Set<String> REDACTED_DATA_KEYS = Set.of(
        "creatorEmail", "creatorIdentifier", "emailAuthEnabled", "contactEmail", "contact_email", "email", "playerUuid");

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

    static TicketVerificationRequiredResponse toVerificationRequiredResponse(String ticketId, String emailHint) {
        return TicketVerificationRequiredResponse.newBuilder()
            .setRequiresVerification(true)
            .setEmailHint(stringValue(emailHint))
            .setTicketId(stringValue(ticketId))
            .build();
    }

    static PublicTicketResponse toPublicTicketResponse(TicketResponse ticket, Ticket rawTicket) {
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
            .setData(toStruct(filterPublicData(ticket.data())))
            .setFormData(toStruct(filterPublicData(ticket.formData())));

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

    static PublicTicketVerificationRequestResponse toRequestVerificationResponse(String emailHint) {
        return PublicTicketVerificationRequestResponse.newBuilder()
            .setSuccess(true)
            .setMessage("Verification code sent")
            .setEmailHint(stringValue(emailHint))
            .build();
    }

    static PublicTicketVerificationResponse toVerifyResponse(String token) {
        return PublicTicketVerificationResponse.newBuilder()
            .setSuccess(true)
            .setToken(stringValue(token))
            .setMessage("Verification successful")
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

    private static Map<String, Object> filterPublicData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> filtered = new HashMap<>(data);
        filtered.keySet().removeAll(REDACTED_DATA_KEYS);
        return filtered;
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
