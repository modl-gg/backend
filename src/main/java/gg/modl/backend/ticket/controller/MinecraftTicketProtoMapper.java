package gg.modl.backend.ticket.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.addAll;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalEpochMillis;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalString;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalTimestamp;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;
import static gg.modl.backend.infrastructure.proto.ProtoValidationSupport.optionalString;

import com.google.protobuf.Struct;
import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.dto.request.MinecraftClaimTicketRequest;
import gg.modl.backend.ticket.dto.request.MinecraftCreateTicketRequest;
import gg.modl.backend.ticket.dto.response.MinecraftPlayerTicketView;
import gg.modl.backend.ticket.dto.response.MinecraftReportView;
import gg.modl.backend.ticket.dto.response.MinecraftTicketDetailReplyView;
import gg.modl.backend.ticket.dto.response.MinecraftTicketDetailView;
import gg.modl.backend.ticket.dto.response.MinecraftTicketListItemView;
import gg.modl.backend.ticket.dto.response.MinecraftTicketLookupView;
import gg.modl.proto.modl.v1.ClaimTicketResponse;
import gg.modl.proto.modl.v1.MinecraftClaimTicketRequestOrBuilder;
import gg.modl.proto.modl.v1.MinecraftCreateTicketRequestOrBuilder;
import gg.modl.proto.modl.v1.MinecraftCreateTicketResponse;
import gg.modl.proto.modl.v1.MinecraftReportOperationResponse;
import gg.modl.proto.modl.v1.MinecraftTicketDetail;
import gg.modl.proto.modl.v1.MinecraftTicketDetailReply;
import gg.modl.proto.modl.v1.MinecraftTicketDetailResponse;
import gg.modl.proto.modl.v1.MinecraftTicketListItem;
import gg.modl.proto.modl.v1.ReportEntry;
import gg.modl.proto.modl.v1.ReportsResponse;
import gg.modl.proto.modl.v1.TicketsResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MinecraftTicketProtoMapper {
    private MinecraftTicketProtoMapper() {
    }

    static TicketsResponse toTicketsResponse(int status, List<MinecraftTicketListItemView> tickets) {
        TicketsResponse.Builder response = TicketsResponse.newBuilder()
            .setStatus(status);
        addAll(tickets, MinecraftTicketProtoMapper::toTicketListItem, response::addTickets);
        return response.build();
    }

    static TicketsResponse toPlayerTicketsResponse(int status, List<MinecraftPlayerTicketView> tickets) {
        TicketsResponse.Builder response = TicketsResponse.newBuilder()
            .setStatus(status);
        addAll(tickets, MinecraftTicketProtoMapper::toTicketListItem, response::addTickets);
        return response.build();
    }

    static TicketsResponse toLookupTicketsResponse(int status, List<MinecraftTicketLookupView> tickets) {
        TicketsResponse.Builder response = TicketsResponse.newBuilder()
            .setStatus(status);
        addAll(tickets, MinecraftTicketProtoMapper::toTicketListItem, response::addTickets);
        return response.build();
    }

    static ReportsResponse toReportsResponse(int status, List<MinecraftReportView> reports) {
        ReportsResponse.Builder response = ReportsResponse.newBuilder()
            .setStatus(status);
        addAll(reports, MinecraftTicketProtoMapper::toReportEntry, response::addReports);
        return response.build();
    }

    static MinecraftTicketDetailResponse toTicketDetailResponse(int status, MinecraftTicketDetailView ticket) {
        return MinecraftTicketDetailResponse.newBuilder()
            .setStatus(status)
            .setTicket(toTicketDetail(ticket))
            .build();
    }

    static MinecraftCreateTicketRequest toCreateTicketRequest(
        MinecraftCreateTicketRequestOrBuilder request
    ) {
        return new MinecraftCreateTicketRequest(
            request.getCreatorUuid(),
            optionalString(request.hasCreatorName(), request.getCreatorName()),
            request.getType(),
            optionalString(request.hasSubject(), request.getSubject()),
            optionalString(request.hasDescription(), request.getDescription()),
            optionalString(request.hasReportedPlayerUuid(), request.getReportedPlayerUuid()),
            optionalString(request.hasReportedPlayerName(), request.getReportedPlayerName()),
            request.getChatMessagesList(),
            request.getTagsList(),
            optionalString(request.hasPriority(), request.getPriority()),
            optionalString(request.hasCreatedServer(), request.getCreatedServer()),
            optionalString(request.hasReplayUrl(), request.getReplayUrl())
        );
    }

    static MinecraftClaimTicketRequest toClaimTicketRequest(
        MinecraftClaimTicketRequestOrBuilder request
    ) {
        return new MinecraftClaimTicketRequest(
            request.getPlayerUuid(),
            request.getPlayerName()
        );
    }

    static MinecraftCreateTicketResponse toCreateTicketResponse(int status, boolean success, String ticketId, String message) {
        return MinecraftCreateTicketResponse.newBuilder()
            .setStatus(status)
            .setSuccess(success)
            .setTicketId(stringValue(ticketId))
            .setMessage(message)
            .build();
    }

    static ClaimTicketResponse toClaimTicketSuccess(String message, String ticketId, String subject) {
        return ClaimTicketResponse.newBuilder()
            .setStatus(200)
            .setSuccess(true)
            .setMessage(message)
            .setTicketId(stringValue(ticketId))
            .setSubject(stringValue(subject))
            .build();
    }

    static MinecraftReportOperationResponse toReportOperationResponse(int status, boolean success, String message) {
        return MinecraftReportOperationResponse.newBuilder()
            .setStatus(status)
            .setSuccess(success)
            .setMessage(message)
            .build();
    }

    private static ReportEntry toReportEntry(MinecraftReportView report) {
        ReportEntry.Builder builder = ReportEntry.newBuilder()
            .setId(stringValue(report.id()))
            .setType(stringValue(report.type()))
            .setCategory(stringValue(report.type()))
            .setReporterName(stringValue(report.reporterName()))
            .setReporterUuid(stringValue(report.reporterUuid()))
            .setReportedPlayerUuid(stringValue(report.reportedPlayerUuid()))
            .setReportedPlayerName(stringValue(report.reportedPlayerName()))
            .setSubject(stringValue(report.subject()))
            .setContent(stringValue(report.content()))
            .setStatus(stringValue(report.status()))
            .setPriority(stringValue(report.priority()))
            .setCreatedAt(longValue(report.createdAt()));

        addAll(report.assignedTo(), Objects::toString, builder::addAssignedTo);
        addAll(report.chatMessages(), MinecraftTicketProtoMapper::toStruct, builder::addChatMessages);
        setOptionalString(builder::setReplayUrl, report.replayUrl());

        return builder.build();
    }

    private static MinecraftTicketListItem toTicketListItem(MinecraftTicketListItemView ticket) {
        MinecraftTicketListItem.Builder builder = MinecraftTicketListItem.newBuilder()
            .setId(stringValue(ticket.id()))
            .setType(stringValue(ticket.type()))
            .setCategory(stringValue(ticket.category()))
            .setSubject(stringValue(ticket.subject()))
            .setStatus(stringValue(ticket.status()))
            .setPlayerName(stringValue(ticket.playerName()))
            .setPlayerUuid(stringValue(ticket.playerUuid()))
            .setPriority(stringValue(ticket.priority()))
            .setCreatedAt(longValue(ticket.createdAt()))
            .setHasStaffResponse(ticket.hasStaffResponse())
            .setLocked(ticket.locked())
            .setReplyCount(ticket.replyCount());

        setOptionalEpochMillis(builder::setUpdatedAt, ticket.updatedAt());
        addAll(ticket.assignedTo(), Objects::toString, builder::addAssignedTo);

        return builder.build();
    }

    private static MinecraftTicketListItem toTicketListItem(MinecraftPlayerTicketView ticket) {
        return MinecraftTicketListItem.newBuilder()
            .setId(stringValue(ticket.id()))
            .setType(stringValue(ticket.type()))
            .setCategory(stringValue(ticket.category()))
            .setSubject(stringValue(ticket.subject()))
            .setStatus(stringValue(ticket.status()))
            .setCreatedAt(longValue(ticket.createdAt()))
            .build();
    }

    private static MinecraftTicketListItem toTicketListItem(MinecraftTicketLookupView ticket) {
        MinecraftTicketListItem.Builder builder = MinecraftTicketListItem.newBuilder()
            .setId(stringValue(ticket.id()))
            .setType(stringValue(ticket.type()))
            .setCategory(stringValue(ticket.category()))
            .setSubject(stringValue(ticket.subject()))
            .setStatus(stringValue(ticket.status()))
            .setPlayerName(stringValue(ticket.playerName()))
            .setPlayerUuid(stringValue(ticket.playerUuid()))
            .setCreatedAt(longValue(ticket.createdAt()));

        setOptionalString(builder::setFirstReplyContent, ticket.firstReplyContent());

        return builder.build();
    }

    private static MinecraftTicketDetail toTicketDetail(MinecraftTicketDetailView ticket) {
        MinecraftTicketDetail.Builder builder = MinecraftTicketDetail.newBuilder()
            .setId(stringValue(ticket.id()))
            .setType(stringValue(ticket.type()))
            .setCategory(stringValue(ticket.category()))
            .setSubject(stringValue(ticket.subject()))
            .setStatus(stringValue(ticket.status()))
            .setPlayerName(stringValue(ticket.playerName()))
            .setPlayerUuid(stringValue(ticket.playerUuid()))
            .setPriority(stringValue(ticket.priority()))
            .setCreatedAt(toTimestamp(ticket.createdAt()))
            .setLocked(ticket.locked());

        setOptionalTimestamp(builder::setUpdatedAt, ticket.updatedAt());
        setOptionalString(builder::setReplayUrl, ticket.replayUrl());
        addAll(ticket.assignedTo(), Objects::toString, builder::addAssignedTo);
        addAll(ticket.replies(), MinecraftTicketProtoMapper::toTicketDetailReply, builder::addReplies);
        addAll(ticket.chatMessages(), MinecraftTicketProtoMapper::toStruct, builder::addChatMessages);

        return builder.build();
    }

    private static MinecraftTicketDetailReply toTicketDetailReply(MinecraftTicketDetailReplyView reply) {
        return MinecraftTicketDetailReply.newBuilder()
            .setId(stringValue(reply.id()))
            .setContent(stringValue(reply.content()))
            .setAuthorName(stringValue(reply.authorName()))
            .setAuthorId(stringValue(reply.authorId()))
            .setIsStaff(reply.isStaff())
            .setCreatedAt(toTimestamp(reply.createdAt()))
            .build();
    }

    private static Struct toStruct(Ticket.ChatMessage chatMessage) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("content", chatMessage.getContent());
        message.put("timestamp", chatMessage.getTimestamp());
        if (chatMessage.getSender() != null) {
            message.put("sender", chatMessage.getSender());
        }
        return ProtoMapperSupport.legacyStruct(message);
    }

}
