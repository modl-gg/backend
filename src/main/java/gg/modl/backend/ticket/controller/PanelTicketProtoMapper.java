package gg.modl.backend.ticket.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.addAll;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.asMap;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.structListToMaps;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.structListToObjects;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.structToMap;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toStruct;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.ai.data.AIAnalysisResult;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketNote;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.request.AddNoteRequest;
import gg.modl.backend.ticket.dto.request.AddReplyRequest;
import gg.modl.backend.ticket.dto.request.BulkTicketUpdateRequest;
import gg.modl.backend.ticket.dto.request.CreateTicketRequest;
import gg.modl.backend.ticket.dto.request.QuickResponseRequest;
import gg.modl.backend.ticket.dto.request.UpdateTicketRequest;
import gg.modl.backend.ticket.dto.response.PaginatedTicketsResponse;
import gg.modl.backend.ticket.dto.response.QuickResponseResult;
import gg.modl.backend.ticket.dto.response.TicketListItemResponse;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import java.util.List;
import java.util.Map;

public final class PanelTicketProtoMapper {
    private PanelTicketProtoMapper() {
    }

    public static gg.modl.proto.modl.v1.TicketResponse toTicketResponse(TicketResponse ticket) {
        gg.modl.proto.modl.v1.TicketResponse.Builder builder = gg.modl.proto.modl.v1.TicketResponse.newBuilder()
            .setId(stringValue(ticket.id()))
            .setType(stringValue(ticket.type()))
            .setCategory(stringValue(ticket.category()))
            .setSubject(stringValue(ticket.subject()))
            .setStatus(stringValue(ticket.status()))
            .setAppealWorkflowStatus(stringValue(ticket.appealWorkflowStatus()))
            .setCreatorName(stringValue(ticket.creatorName()))
            .setCreatorUuid(stringValue(ticket.creatorUuid()))
            .setReportedBy(stringValue(ticket.reportedBy()))
            .setReportedPlayer(stringValue(ticket.reportedPlayer()))
            .setReportedPlayerUuid(stringValue(ticket.reportedPlayerUuid()))
            .setDate(longValue(ticket.date()))
            .setLocked(ticket.locked())
            .setEmailAuthEnabled(ticket.emailAuthEnabled())
            .setHidden(ticket.hidden())
            .setReplayUrl(stringValue(ticket.replayUrl()));

        addAll(ticket.messages(), PanelTicketProtoMapper::toTicketReply, builder::addMessages);
        addAll(ticket.notes(), PanelTicketProtoMapper::toTicketNote, builder::addNotes);
        addAll(ticket.tags(), value -> value, builder::addTags);
        addAll(ticket.chatMessages(), PanelTicketProtoMapper::toChatMessage, builder::addChatMessages);
        addAll(ticket.assignedTo(), value -> value, builder::addAssignedTo);

        if (ticket.formData() != null) {
            builder.setFormData(toStruct(ticket.formData()));
        }
        if (ticket.data() != null) {
            builder.setData(toStruct(ticket.data()));
        }
        if (ticket.aiAnalysis() != null) {
            builder.setAiAnalysis(toAiAnalysis(ticket.aiAnalysis()));
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.PaginatedTicketsResponse toPaginatedTicketsResponse(PaginatedTicketsResponse response) {
        gg.modl.proto.modl.v1.PaginatedTicketsResponse.Builder builder =
            gg.modl.proto.modl.v1.PaginatedTicketsResponse.newBuilder();

        addAll(response.tickets(), PanelTicketProtoMapper::toTicketListItem, builder::addTickets);

        PaginatedTicketsResponse.PaginationInfo pagination = response.pagination();
        if (pagination != null) {
            builder.setPagination(gg.modl.proto.modl.v1.PaginatedTicketsResponse.PaginatedTicketsPaginationInfo.newBuilder()
                .setCurrent(pagination.current())
                .setTotal(pagination.total())
                .setLimit(pagination.limit())
                .setTotalTickets(pagination.totalTickets())
                .setHasNext(pagination.hasNext())
                .setHasPrev(pagination.hasPrev()));
        }

        PaginatedTicketsResponse.FiltersInfo filters = response.filters();
        if (filters != null) {
            gg.modl.proto.modl.v1.PaginatedTicketsResponse.PaginatedTicketsFiltersInfo.Builder filtersBuilder =
                gg.modl.proto.modl.v1.PaginatedTicketsResponse.PaginatedTicketsFiltersInfo.newBuilder()
                    .setSearch(stringValue(filters.search()))
                    .setStatus(stringValue(filters.status()));
            addAll(filters.types(), value -> value, filtersBuilder::addTypes);
            builder.setFilters(filtersBuilder);
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.TicketCountsResponse toTicketCountsResponse(Map<String, Long> counts) {
        return gg.modl.proto.modl.v1.TicketCountsResponse.newBuilder()
            .setOpen(longValue(counts.get("open")))
            .setClosed(longValue(counts.get("closed")))
            .build();
    }

    static gg.modl.proto.modl.v1.BulkTicketUpdateResponse toBulkTicketUpdateResponse(int updated, String message) {
        return gg.modl.proto.modl.v1.BulkTicketUpdateResponse.newBuilder()
            .setUpdated(updated)
            .setMessage(stringValue(message))
            .build();
    }

    static gg.modl.proto.modl.v1.QuickResponseResult toQuickResponseResult(QuickResponseResult result) {
        return gg.modl.proto.modl.v1.QuickResponseResult.newBuilder()
            .setSuccess(result.success())
            .setMessage(stringValue(result.message()))
            .setTicketId(stringValue(result.ticketId()))
            .setActionName(stringValue(result.actionName()))
            .setTicketClosed(result.ticketClosed())
            .setPunishmentIssued(result.punishmentIssued())
            .setAppealOutcome(stringValue(result.appealOutcome()))
            .build();
    }

    static gg.modl.proto.modl.v1.AddTicketReplyResponse toAddReplyResponse(TicketReply reply) {
        return gg.modl.proto.modl.v1.AddTicketReplyResponse.newBuilder()
            .setSuccess(true)
            .setReply(toPublicTicketReply(reply))
            .build();
    }

    static gg.modl.proto.modl.v1.TicketTagsResponse toTagsResponse(List<String> tags) {
        gg.modl.proto.modl.v1.TicketTagsResponse.Builder builder =
            gg.modl.proto.modl.v1.TicketTagsResponse.newBuilder();
        addAll(tags, value -> value, builder::addTags);
        return builder.build();
    }

    static gg.modl.proto.modl.v1.TicketNote toTicketNoteResponse(TicketNote note) {
        return toTicketNote(note);
    }

    static gg.modl.proto.modl.v1.TicketSubscriptionsResponse toTicketSubscriptionsResponse(
        List<gg.modl.backend.ticket.dto.response.TicketSubscriptionResponse> subscriptions
    ) {
        gg.modl.proto.modl.v1.TicketSubscriptionsResponse.Builder builder =
            gg.modl.proto.modl.v1.TicketSubscriptionsResponse.newBuilder();
        addAll(subscriptions, PanelTicketProtoMapper::toTicketSubscription, builder::addSubscriptions);
        return builder.build();
    }

    static gg.modl.proto.modl.v1.SubscriptionUpdatesResponse toSubscriptionUpdatesResponse(
        List<gg.modl.backend.ticket.dto.response.SubscriptionUpdateResponse> updates
    ) {
        gg.modl.proto.modl.v1.SubscriptionUpdatesResponse.Builder builder =
            gg.modl.proto.modl.v1.SubscriptionUpdatesResponse.newBuilder();
        addAll(updates, PanelTicketProtoMapper::toSubscriptionUpdate, builder::addUpdates);
        return builder.build();
    }

    static gg.modl.proto.modl.v1.DeleteTicketSubscriptionResponse toDeleteSubscriptionResponse(String message) {
        return gg.modl.proto.modl.v1.DeleteTicketSubscriptionResponse.newBuilder()
            .setMessage(stringValue(message))
            .build();
    }

    static gg.modl.proto.modl.v1.MarkSubscriptionUpdateReadResponse toMarkUpdateReadResponse(String message, boolean modified) {
        return gg.modl.proto.modl.v1.MarkSubscriptionUpdateReadResponse.newBuilder()
            .setMessage(stringValue(message))
            .setModified(modified)
            .build();
    }

    static gg.modl.proto.modl.v1.MarkTicketSubscriptionReadResponse toMarkTicketReadResponse(String message) {
        return gg.modl.proto.modl.v1.MarkTicketSubscriptionReadResponse.newBuilder()
            .setMessage(stringValue(message))
            .build();
    }

    private static gg.modl.proto.modl.v1.TicketSubscriptionResponse toTicketSubscription(
        gg.modl.backend.ticket.dto.response.TicketSubscriptionResponse subscription
    ) {
        return gg.modl.proto.modl.v1.TicketSubscriptionResponse.newBuilder()
            .setTicketId(stringValue(subscription.ticketId()))
            .setTicketTitle(stringValue(subscription.ticketTitle()))
            .setSubscribedAt(longValue(subscription.subscribedAt()))
            .build();
    }

    private static gg.modl.proto.modl.v1.SubscriptionUpdateResponse toSubscriptionUpdate(
        gg.modl.backend.ticket.dto.response.SubscriptionUpdateResponse update
    ) {
        gg.modl.proto.modl.v1.SubscriptionUpdateResponse.Builder builder =
            gg.modl.proto.modl.v1.SubscriptionUpdateResponse.newBuilder()
                .setId(stringValue(update.id()))
                .setTicketId(stringValue(update.ticketId()))
                .setTicketTitle(stringValue(update.ticketTitle()))
                .setReplyContent(stringValue(update.replyContent()))
                .setReplyBy(stringValue(update.replyBy()))
                .setReplyAt(longValue(update.replyAt()))
                .setIsStaffReply(update.isStaffReply())
                .setIsRead(update.isRead());
        if (update.additionalCount() != null) {
            builder.setAdditionalCount(update.additionalCount());
        }
        return builder.build();
    }

    static CreateTicketRequest fromCreateTicketRequest(gg.modl.proto.modl.v1.CreateTicketRequest request) {
        return new CreateTicketRequest(
            request.getType(),
            request.hasSubject() ? request.getSubject() : null,
            request.hasDescription() ? request.getDescription() : null,
            request.hasCreatorUuid() ? request.getCreatorUuid() : null,
            request.hasCreatorName() ? request.getCreatorName() : null,
            request.hasCreatorEmail() ? request.getCreatorEmail() : null,
            request.hasReportedPlayerUuid() ? request.getReportedPlayerUuid() : null,
            request.hasReportedPlayerName() ? request.getReportedPlayerName() : null,
            structListToMaps(request.getChatMessagesList()),
            request.hasFormData() ? structToMap(request.getFormData()) : null,
            structListToObjects(request.getAttachmentsList()),
            request.getTagsList().isEmpty() ? null : List.copyOf(request.getTagsList()),
            request.hasPriority() ? request.getPriority() : null,
            request.hasCreatorIdentifier() ? request.getCreatorIdentifier() : null,
            request.hasEmailAuthEnabled() ? request.getEmailAuthEnabled() : null,
            request.getFieldLabelsMap().isEmpty() ? null : Map.copyOf(request.getFieldLabelsMap())
        );
    }

    static UpdateTicketRequest fromUpdateTicketRequest(gg.modl.proto.modl.v1.UpdateTicketRequest request) {
        List<String> tags = request.getTagsList().isEmpty() ? null : List.copyOf(request.getTagsList());
        if (tags != null) {
            if (tags.size() > RequestValidationLimits.TICKET_TAGS_MAX_ENTRIES) {
                throw new ValidationException("tags exceeds maximum of " + RequestValidationLimits.TICKET_TAGS_MAX_ENTRIES + " entries");
            }
            for (String tag : tags) {
                if (tag != null && tag.length() > RequestValidationLimits.TICKET_TAG_MAX_LENGTH) {
                    throw new ValidationException("tag exceeds maximum length of " + RequestValidationLimits.TICKET_TAG_MAX_LENGTH);
                }
            }
        }

        Map<String, Object> data = request.hasData() ? structToMap(request.getData()) : null;
        if (data != null && data.size() > RequestValidationLimits.TICKET_DATA_MAX_ENTRIES) {
            throw new ValidationException("data exceeds maximum of " + RequestValidationLimits.TICKET_DATA_MAX_ENTRIES + " entries");
        }

        List<String> assignedTo = request.getAssignedToList().isEmpty() ? null : List.copyOf(request.getAssignedToList());
        if (assignedTo != null) {
            for (String assignee : assignedTo) {
                if (assignee != null && assignee.length() > RequestValidationLimits.TICKET_ASSIGNEE_MAX_LENGTH) {
                    throw new ValidationException("assignee exceeds maximum length of " + RequestValidationLimits.TICKET_ASSIGNEE_MAX_LENGTH);
                }
            }
        }

        return new UpdateTicketRequest(
            request.hasStatus() ? request.getStatus() : null,
            request.hasLocked() ? request.getLocked() : null,
            request.hasNewReply() ? fromAddReplyRequest(request.getNewReply()) : null,
            request.hasNewNote() ? fromAddNoteRequest(request.getNewNote()) : null,
            tags,
            data,
            request.hasHidden() ? request.getHidden() : null,
            assignedTo
        );
    }

    static AddReplyRequest fromAddReplyRequest(gg.modl.proto.modl.v1.AddReplyRequest request) {
        return new AddReplyRequest(
            request.getName(),
            request.getContent(),
            request.hasType() ? request.getType() : null,
            request.getStaff(),
            request.hasAvatar() ? request.getAvatar() : null,
            structListToObjects(request.getAttachmentsList()),
            request.hasAction() ? request.getAction() : null,
            request.hasCreatorIdentifier() ? request.getCreatorIdentifier() : null
        );
    }

    static AddNoteRequest fromAddNoteRequest(gg.modl.proto.modl.v1.AddNoteRequest request) {
        return new AddNoteRequest(
            request.getText(),
            request.getIssuerName(),
            request.hasIssuerAvatar() ? request.getIssuerAvatar() : null
        );
    }

    static BulkTicketUpdateRequest fromBulkTicketUpdateRequest(gg.modl.proto.modl.v1.BulkTicketUpdateRequest request) {
        return new BulkTicketUpdateRequest(
            List.copyOf(request.getTicketIdsList()),
            request.hasLocked() ? request.getLocked() : null,
            request.getAddLabelsList().isEmpty() ? null : List.copyOf(request.getAddLabelsList()),
            request.getRemoveLabelsList().isEmpty() ? null : List.copyOf(request.getRemoveLabelsList()),
            request.hasAssignTo() ? request.getAssignTo() : null,
            request.hasHidden() ? request.getHidden() : null
        );
    }

    static QuickResponseRequest fromQuickResponseRequest(gg.modl.proto.modl.v1.QuickResponseRequest request) {
        return new QuickResponseRequest(
            request.getActionId(),
            request.getCategoryId(),
            request.hasPunishmentTypeId() ? request.getPunishmentTypeId() : null,
            request.hasPunishmentSeverity() ? request.getPunishmentSeverity() : null,
            request.hasCustomValues() ? structToMap(request.getCustomValues()) : null,
            request.hasAppealAction() ? request.getAppealAction() : null
        );
    }

    private static gg.modl.proto.modl.v1.TicketListItemResponse toTicketListItem(TicketListItemResponse item) {
        gg.modl.proto.modl.v1.TicketListItemResponse.Builder builder =
            gg.modl.proto.modl.v1.TicketListItemResponse.newBuilder()
                .setId(stringValue(item.id()))
                .setSubject(stringValue(item.subject()))
                .setStatus(stringValue(item.status()))
                .setReportedBy(stringValue(item.reportedBy()))
                .setReportedByName(stringValue(item.reportedByName()))
                .setDate(longValue(item.date()))
                .setCategory(stringValue(item.category()))
                .setLocked(item.locked())
                .setType(stringValue(item.type()))
                .setReplyCount(item.replyCount())
                .setHidden(item.hidden());

        if (item.lastReply() != null) {
            builder.setLastReply(toTicketReply(item.lastReply()));
        }
        addAll(item.tags(), value -> value, builder::addTags);
        addAll(item.assignedTo(), value -> value, builder::addAssignedTo);
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.TicketReply toTicketReply(TicketReply reply) {
        gg.modl.proto.modl.v1.TicketReply.Builder builder = gg.modl.proto.modl.v1.TicketReply.newBuilder()
            .setId(stringValue(reply.getId()))
            .setName(stringValue(reply.getName()))
            .setAvatar(stringValue(reply.getAvatar()))
            .setContent(stringValue(reply.getContent()))
            .setType(stringValue(reply.getType()))
            .setCreated(longValue(reply.getCreated()))
            .setStaff(reply.isStaff())
            .setAction(stringValue(reply.getAction()))
            .setCreatorIdentifier(stringValue(reply.getCreatorIdentifier()));
        addAll(reply.getAttachments(), value -> toStruct(asMap(value)), builder::addAttachments);
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.PublicTicketReply toPublicTicketReply(TicketReply reply) {
        gg.modl.proto.modl.v1.PublicTicketReply.Builder builder = gg.modl.proto.modl.v1.PublicTicketReply.newBuilder()
            .setId(stringValue(reply.getId()))
            .setName(stringValue(reply.getName()))
            .setAvatar(stringValue(reply.getAvatar()))
            .setContent(stringValue(reply.getContent()))
            .setType(stringValue(reply.getType()))
            .setStaff(reply.isStaff())
            .setAction(stringValue(reply.getAction()))
            .setCreatorIdentifier(stringValue(reply.getCreatorIdentifier()));
        if (reply.getCreated() != null) {
            builder.setCreated(toTimestamp(reply.getCreated()));
        }
        addAll(reply.getAttachments(), value -> toStruct(asMap(value)), builder::addAttachments);
        return builder.build();
    }

    private static gg.modl.proto.modl.v1.TicketNote toTicketNote(TicketNote note) {
        return gg.modl.proto.modl.v1.TicketNote.newBuilder()
            .setText(stringValue(note.getText()))
            .setIssuerName(stringValue(note.getIssuerName()))
            .setIssuerAvatar(stringValue(note.getIssuerAvatar()))
            .setDate(longValue(note.getDate()))
            .build();
    }

    private static gg.modl.proto.modl.v1.TicketChatMessage toChatMessage(Ticket.ChatMessage message) {
        return gg.modl.proto.modl.v1.TicketChatMessage.newBuilder()
            .setContent(stringValue(message.getContent()))
            .setTimestamp(longValue(message.getTimestamp()))
            .setSender(stringValue(message.getSender()))
            .build();
    }

    private static gg.modl.proto.modl.v1.AIAnalysisResult toAiAnalysis(AIAnalysisResult analysis) {
        gg.modl.proto.modl.v1.AIAnalysisResult.Builder builder = gg.modl.proto.modl.v1.AIAnalysisResult.newBuilder()
            .setAnalysis(stringValue(analysis.getAnalysis()))
            .setCreatedAt(longValue(analysis.getCreatedAt()))
            .setRawResponse(stringValue(analysis.getRawResponse()))
            .setWasAppliedAutomatically(analysis.isWasAppliedAutomatically())
            .setDismissed(analysis.isDismissed());

        AIAnalysisResult.SuggestedAction action = analysis.getSuggestedAction();
        if (action != null) {
            builder.setSuggestedAction(gg.modl.proto.modl.v1.AIAnalysisResult.SuggestedAction.newBuilder()
                .setPunishmentTypeId(action.getPunishmentTypeId())
                .setSeverity(stringValue(action.getSeverity())));
        }
        return builder.build();
    }

}
