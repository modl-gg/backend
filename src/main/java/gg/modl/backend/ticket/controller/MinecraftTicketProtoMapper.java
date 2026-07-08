package gg.modl.backend.ticket.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.booleanValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.intValueOrZero;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.list;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.listOfMaps;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.map;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalString;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoValidationSupport.optionalString;

import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import gg.modl.backend.infrastructure.proto.ProtoMapperSupport;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.dto.request.MinecraftClaimTicketRequest;
import gg.modl.backend.ticket.dto.request.MinecraftCreateTicketRequest;
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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class MinecraftTicketProtoMapper {
    private MinecraftTicketProtoMapper() {
    }

    static TicketsResponse toTicketsResponse(int status, List<Map<String, Object>> tickets) {
        TicketsResponse.Builder response = TicketsResponse.newBuilder()
            .setStatus(status);
        tickets.stream()
            .map(MinecraftTicketProtoMapper::toTicketListItem)
            .forEach(response::addTickets);
        return response.build();
    }

    static ReportsResponse toReportsResponse(int status, List<Map<String, Object>> reports) {
        ReportsResponse.Builder response = ReportsResponse.newBuilder()
            .setStatus(status);
        reports.stream()
            .map(MinecraftTicketProtoMapper::toReportEntry)
            .forEach(response::addReports);
        return response.build();
    }

    static MinecraftTicketDetailResponse toTicketDetailResponse(int status, Map<String, Object> ticket) {
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

    private static ReportEntry toReportEntry(Map<String, Object> report) {
        ReportEntry.Builder builder = ReportEntry.newBuilder()
            .setId(stringValue(report.get("id")))
            .setType(stringValue(report.get("type")))
            .setCategory(stringValue(report.getOrDefault("category", report.get("type"))))
            .setReporterName(stringValue(report.get("reporterName")))
            .setReporterUuid(stringValue(report.get("reporterUuid")))
            .setReportedPlayerUuid(stringValue(report.get("reportedPlayerUuid")))
            .setReportedPlayerName(stringValue(report.get("reportedPlayerName")))
            .setSubject(stringValue(report.get("subject")))
            .setContent(stringValue(report.get("content")))
            .setStatus(stringValue(report.get("status")))
            .setPriority(stringValue(report.get("priority")))
            .setCreatedAt(epochMillis(report.get("createdAt")));

        list(report.get("assignedTo")).stream()
            .map(Objects::toString)
            .forEach(builder::addAssignedTo);
        list(report.get("chatMessages")).stream()
            .map(MinecraftTicketProtoMapper::toStruct)
            .forEach(builder::addChatMessages);
        setOptionalString(builder::setReplayUrl, report.get("replayUrl"));

        return builder.build();
    }

    private static MinecraftTicketListItem toTicketListItem(Map<String, Object> ticket) {
        MinecraftTicketListItem.Builder builder = MinecraftTicketListItem.newBuilder()
            .setId(stringValue(ticket.get("id")))
            .setType(stringValue(ticket.get("type")))
            .setCategory(stringValue(ticket.get("category")))
            .setSubject(stringValue(ticket.get("subject")))
            .setStatus(stringValue(ticket.get("status")))
            .setPlayerName(stringValue(ticket.get("playerName")))
            .setPlayerUuid(stringValue(ticket.get("playerUuid")))
            .setPriority(stringValue(ticket.get("priority")))
            .setCreatedAt(epochMillis(ticket.get("createdAt")))
            .setHasStaffResponse(booleanValue(ticket.get("hasStaffResponse")))
            .setLocked(booleanValue(ticket.get("locked")))
            .setReplyCount(intValueOrZero(ticket.get("replyCount")));

        setOptionalString(builder::setFirstReplyContent, ticket.get("firstReplyContent"));
        setOptionalLong(builder::setUpdatedAt, ticket.get("updatedAt"));
        list(ticket.get("assignedTo")).stream()
            .map(Objects::toString)
            .forEach(builder::addAssignedTo);

        return builder.build();
    }

    private static MinecraftTicketDetail toTicketDetail(Map<String, Object> ticket) {
        MinecraftTicketDetail.Builder builder = MinecraftTicketDetail.newBuilder()
            .setId(stringValue(ticket.get("id")))
            .setType(stringValue(ticket.get("type")))
            .setCategory(stringValue(ticket.get("category")))
            .setSubject(stringValue(ticket.get("subject")))
            .setStatus(stringValue(ticket.get("status")))
            .setPlayerName(stringValue(ticket.get("playerName")))
            .setPlayerUuid(stringValue(ticket.get("playerUuid")))
            .setPriority(stringValue(ticket.get("priority")))
            .setCreatedAt(timestampValue(ticket.get("createdAt")))
            .setLocked(booleanValue(ticket.get("locked")));

        setOptionalTimestamp(builder::setUpdatedAt, ticket.get("updatedAt"));
        setOptionalString(builder::setReplayUrl, ticket.get("replayUrl"));
        list(ticket.get("assignedTo")).stream()
            .map(Objects::toString)
            .forEach(builder::addAssignedTo);
        listOfMaps(ticket.get("replies")).stream()
            .map(MinecraftTicketProtoMapper::toTicketDetailReply)
            .forEach(builder::addReplies);
        list(ticket.get("chatMessages")).stream()
            .map(MinecraftTicketProtoMapper::toStruct)
            .forEach(builder::addChatMessages);

        return builder.build();
    }

    private static MinecraftTicketDetailReply toTicketDetailReply(Map<String, Object> reply) {
        return MinecraftTicketDetailReply.newBuilder()
            .setId(stringValue(reply.get("id")))
            .setContent(stringValue(reply.get("content")))
            .setAuthorName(stringValue(reply.get("authorName")))
            .setAuthorId(stringValue(reply.get("authorId")))
            .setIsStaff(booleanValue(reply.get("isStaff")))
            .setCreatedAt(timestampValue(reply.get("createdAt")))
            .build();
    }

    private static Struct toStruct(Object value) {
        if (value instanceof Ticket.ChatMessage chatMessage) {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("content", chatMessage.getContent());
            message.put("timestamp", chatMessage.getTimestamp());
            if (chatMessage.getSender() != null) {
                message.put("sender", chatMessage.getSender());
            }
            return ProtoMapperSupport.legacyStruct(message);
        }
        return ProtoMapperSupport.legacyStruct(map(value));
    }

    private static void setOptionalLong(Consumer<Long> setter, Object value) {
        if (value != null) {
            setter.accept(epochMillis(value));
        }
    }

    private static void setOptionalTimestamp(Consumer<Timestamp> setter, Object value) {
        if (value != null) {
            setter.accept(timestampValue(value));
        }
    }

    private static Timestamp timestampValue(Object value) {
        Instant instant = instantValue(value);
        return Timestamp.newBuilder()
            .setSeconds(instant.getEpochSecond())
            .setNanos(instant.getNano())
            .build();
    }

    private static Instant instantValue(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        if (value instanceof CharSequence chars) {
            try {
                return Instant.parse(chars);
            } catch (DateTimeParseException ignored) {
                try {
                    return Instant.ofEpochMilli(Long.parseLong(chars.toString()));
                } catch (NumberFormatException ignoredToo) {
                    return Instant.EPOCH;
                }
            }
        }
        return Instant.EPOCH;
    }

    private static long epochMillis(Object value) {
        if (value instanceof Date date) {
            return date.getTime();
        }
        if (value instanceof Instant instant) {
            return instant.toEpochMilli();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence chars) {
            try {
                return Long.parseLong(chars.toString());
            } catch (NumberFormatException ignored) {
                return instantValue(value).toEpochMilli();
            }
        }
        return 0L;
    }

}
