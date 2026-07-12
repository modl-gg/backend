package gg.modl.backend.ticket.service;

import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.replay.util.ReplayReferenceUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.AppealWorkflowStatus;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketPriority;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import gg.modl.backend.ticket.dto.request.AssignReportRequest;
import gg.modl.backend.ticket.dto.request.DismissReportRequest;
import gg.modl.backend.ticket.dto.request.MinecraftClaimTicketRequest;
import gg.modl.backend.ticket.dto.request.MinecraftCreateTicketRequest;
import gg.modl.backend.ticket.dto.request.ResolveReportRequest;
import gg.modl.backend.ticket.dto.response.MinecraftPlayerTicketView;
import gg.modl.backend.ticket.dto.response.MinecraftReportView;
import gg.modl.backend.ticket.dto.response.MinecraftTicketDetailReplyView;
import gg.modl.backend.ticket.dto.response.MinecraftTicketDetailView;
import gg.modl.backend.ticket.dto.response.MinecraftTicketListItemView;
import gg.modl.backend.ticket.dto.response.MinecraftTicketLookupView;
import gg.modl.backend.infrastructure.util.UuidUtils;
import gg.modl.backend.ticket.util.TicketAssigneeUtil;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MinecraftTicketService {
    private static final Pattern CHAT_LINE_PATTERN =
        Pattern.compile("^(?:(\\d{1,2}:\\d{2}:\\d{2})\\s+)?([^:]{1,48}):\\s(.*)$", Pattern.DOTALL);
    private static final DateTimeFormatter CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm:ss");

    private final TicketMongoRepository ticketRepository;
    private final TicketNotificationService notificationService;
    private final TicketIdGenerator ticketIdGenerator;

    public Ticket createMinecraftTicket(Server server, MinecraftCreateTicketRequest request) {
        return createMinecraftTicketInternal(server, request, false);
    }

    private Ticket createMinecraftTicketInternal(Server server, MinecraftCreateTicketRequest request, boolean unfinished) {
        TicketCategory ticketCategory = TicketCategory.fromCanonicalId(request.type());
        Date now = new Date();

        List<Ticket.ChatMessage> chatMessages = new ArrayList<>();
        if (!unfinished && request.chatMessages() != null && !request.chatMessages().isEmpty()) {
            for (String message : request.chatMessages()) {
                if (message == null || message.isBlank()) {
                    continue;
                }
                chatMessages.add(parseChatMessage(message, now));
            }
        }

        Map<String, Object> ticketData = new HashMap<>();
        if (request.createdServer() != null && !request.createdServer().isBlank()) {
            ticketData.put("createdServer", request.createdServer());
        }

        Ticket ticket = Ticket.builder()
            .type(ticketCategory)
            .subject(request.subject())
            .status(unfinished ? TicketStatus.UNFINISHED : TicketStatus.OPEN)
            .appealWorkflowStatus(ticketCategory.isAppeal() ? AppealWorkflowStatus.OPEN : null)
            .creatorUuid(UuidUtils.normalize(request.creatorUuid()))
            .creatorName(request.creatorName())
            .reportedPlayer(request.reportedPlayerName())
            .reportedPlayerUuid(UuidUtils.normalize(request.reportedPlayerUuid()))
            .tags(request.tags() != null ? new ArrayList<>(request.tags()) : new ArrayList<>())
            .replies(new ArrayList<>())
            .notes(new ArrayList<>())
            .chatMessages(unfinished ? null : chatMessages)
            .data(ticketData.isEmpty() ? null : ticketData)
            .priority(TicketPriority.resolveOrDefault(request.priority()))
            .replayUrl(request.replayUrl())
            .replayId(ReplayReferenceUtil.extractReplayId(request.replayUrl()))
            .locked(false)
            .created(now)
            .updatedAt(now)
            .build();

        if (!unfinished && request.description() != null && !request.description().isBlank()) {
            TicketReply initialReply = TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .content(request.description())
                .name(request.creatorName() != null ? request.creatorName() : "Player")
                .creatorIdentifier(UuidUtils.normalize(request.creatorUuid()))
                .staff(false)
                .type("user")
                .created(now)
                .build();
            ticket.getReplies().add(initialReply);
        }

        return ticketIdGenerator.insertWithUniqueId(server, ticketCategory.getTicketPrefix(), ticket);
    }

    public Ticket createUnfinishedMinecraftTicket(Server server, MinecraftCreateTicketRequest request) {
        return createMinecraftTicketInternal(server, request, true);
    }

    public List<Ticket> getMinecraftTickets(Server server, String status, String type, int limit) {
        return ticketRepository.findMinecraftTickets(server, status, type, limit);
    }

    public Optional<Ticket> getMinecraftTicket(Server server, String ticketId) {
        return ticketRepository.findById(server, ticketId);
    }

    public List<Ticket> getMinecraftTicketsByCreator(Server server, String creatorUuid, int limit) {
        return ticketRepository.findRecentByCreator(server, UuidUtils.normalize(creatorUuid), limit);
    }

    public MinecraftTicketClaimResult claimMinecraftTicket(Server server, String ticketId, MinecraftClaimTicketRequest request) {
        Optional<Ticket> existingTicket = ticketRepository.findById(server, ticketId);
        if (existingTicket.isEmpty()) {
            return new MinecraftTicketClaimResult(MinecraftTicketClaimStatus.NOT_FOUND, null);
        }

        Ticket ticket = existingTicket.get();
        String claimerUuid = UuidUtils.normalize(request.playerUuid());
        if (ticket.getCreatorUuid() != null && !ticket.getCreatorUuid().isBlank()) {
            MinecraftTicketClaimStatus status = Objects.equals(UuidUtils.normalize(ticket.getCreatorUuid()), claimerUuid)
                ? MinecraftTicketClaimStatus.SUCCESS
                : MinecraftTicketClaimStatus.ALREADY_LINKED;
            return new MinecraftTicketClaimResult(status, ticket);
        }
        String oldCreatorName = ticket.getCreatorName();
        String originalCreatorIdentifier = resolveOriginalCreatorIdentifier(ticket);
        ticket.setCreatorUuid(claimerUuid);
        ticket.setCreatorName(request.playerName());
        ticket.setUpdatedAt(new Date());

        if (ticket.getReplies() != null) {
            for (TicketReply reply : ticket.getReplies()) {
                if (reply.isStaff()) {
                    continue;
                }
                String replyIdentifier = reply.getCreatorIdentifier();
                boolean owned;
                if (replyIdentifier != null && !replyIdentifier.isBlank()) {
                    owned = originalCreatorIdentifier != null && replyIdentifier.equals(originalCreatorIdentifier);
                } else {
                    owned = oldCreatorName != null && oldCreatorName.equals(reply.getName());
                }
                if (owned) {
                    reply.setName(request.playerName());
                    reply.setCreatorIdentifier(claimerUuid);
                }
            }
        }

        Ticket saved = ticketRepository.saveEntity(server, ticket);
        return new MinecraftTicketClaimResult(MinecraftTicketClaimStatus.SUCCESS, saved);
    }

    private String resolveOriginalCreatorIdentifier(Ticket ticket) {
        if (ticket.getData() != null) {
            Object stored = ticket.getData().get("creatorIdentifier");
            if (stored instanceof String identifier && !identifier.isBlank()) {
                return identifier;
            }
        }
        if (ticket.getReplies() != null) {
            for (TicketReply reply : ticket.getReplies()) {
                if (reply.isStaff()) {
                    continue;
                }
                String identifier = reply.getCreatorIdentifier();
                if (identifier != null && !identifier.isBlank()) {
                    return identifier;
                }
            }
        }
        return null;
    }

    public List<Ticket> getMinecraftTicketsByIds(Server server, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ticketRepository.findByIds(server, ids);
    }

    public List<MinecraftReportView> getMinecraftReports(Server server, String status, int limit) {
        return ticketRepository.findReports(server, status, null, limit, true)
            .stream()
            .map(this::toMinecraftReport)
            .toList();
    }

    private MinecraftReportView toMinecraftReport(Ticket ticket) {
        return new MinecraftReportView(
            ticket.getId(),
            ticket.getType() != null ? ticket.getType().getId() : null,
            ticket.getCreatorName() != null ? ticket.getCreatorName() : "Unknown",
            ticket.getCreatorUuid(),
            ticket.getReportedPlayerUuid(),
            ticket.getReportedPlayer(),
            ticket.getSubject(),
            ticket.getReplies() != null && !ticket.getReplies().isEmpty()
                ? ticket.getReplies().get(0).getContent()
                : null,
            ticket.getStatus() != null ? ticket.getStatus().getId() : TicketStatus.OPEN.getId(),
            (ticket.getPriority() != null ? ticket.getPriority() : TicketPriority.NORMAL).getId(),
            ticket.getCreated(),
            ticket.getAssignedTo(),
            ticket.getChatMessages(),
            ticket.getReplayUrl()
        );
    }

    public List<MinecraftReportView> getMinecraftReportsForPlayer(Server server, String playerUuid, String status, int limit) {
        return ticketRepository.findReports(server, status, UuidUtils.normalize(playerUuid), limit, false)
            .stream()
            .map(this::toMinecraftReport)
            .toList();
    }

    public ReportOperationResult dismissMinecraftReport(Server server, String ticketId, DismissReportRequest request) {
        return closeReport(server, ticketId, request.dismissedBy(),
            "Thank you for submitting this report. After careful review, we have found insufficient evidence to take action at this time.",
            (ticket, now) -> {
                if (request.reason() != null && !request.reason().isBlank()) {
                    ensureTicketData(ticket).put("dismissReason", request.reason());
                }
                if (request.dismissedBy() != null) {
                    Map<String, Object> data = ensureTicketData(ticket);
                    data.put("dismissedBy", request.dismissedBy());
                    data.put("dismissedAt", now);
                }
            });
    }

    private ReportOperationResult closeReport(
        Server server,
        String ticketId,
        String staffName,
        String closeMessage,
        BiConsumer<Ticket, Date> applyMetadata
    ) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return new ReportOperationResult(ReportOperationStatus.NOT_FOUND, null);
        }
        if (ticket.isLocked() || (ticket.getStatus() != null && ticket.getStatus().isTerminal())) {
            return new ReportOperationResult(ReportOperationStatus.SUCCESS, ticket);
        }

        Date now = new Date();
        TicketReply reply = TicketReply.builder()
            .id(UUID.randomUUID().toString())
            .name(staffName != null ? staffName : "Staff")
            .content(closeMessage)
            .type("reply")
            .created(now)
            .staff(true)
            .action("close")
            .build();

        ticket.ensureReplies().add(reply);
        ticket.applyLifecycleStatus(TicketStatus.CLOSED);
        ticket.setUpdatedAt(now);
        applyMetadata.accept(ticket, now);

        Ticket saved = ticketRepository.saveEntity(server, ticket);
        notificationService.notifyTicketReply(server, saved, reply);
        notificationService.notifyTicketClosed(server, saved);
        return new ReportOperationResult(ReportOperationStatus.SUCCESS, saved);
    }

    public ReportOperationResult dismissMinecraftReport(
        Server server,
        String ticketId,
        String dismissedBy,
        String reason
    ) {
        return dismissMinecraftReport(server, ticketId, new DismissReportRequest(dismissedBy, reason));
    }

    private Map<String, Object> ensureTicketData(Ticket ticket) {
        if (ticket.getData() == null) {
            ticket.setData(new HashMap<>());
        }
        return ticket.getData();
    }

    public ReportOperationResult resolveMinecraftReport(Server server, String ticketId, ResolveReportRequest request) {
        return closeReport(server, ticketId, request.resolvedBy(),
            "Thank you for creating this report. After careful review, we have accepted this and the reported player has received a punishment.",
            (ticket, now) -> {
                if (request.resolution() != null && !request.resolution().isBlank()) {
                    ensureTicketData(ticket).put("resolution", request.resolution());
                }
                if (request.resolvedBy() != null) {
                    Map<String, Object> data = ensureTicketData(ticket);
                    data.put("resolvedBy", request.resolvedBy());
                    data.put("resolvedAt", now);
                }
                if (request.punishmentId() != null) {
                    ensureTicketData(ticket).put("linkedPunishmentId", request.punishmentId());
                }
            });
    }

    public ReportOperationResult resolveMinecraftReport(
        Server server,
        String ticketId,
        String resolvedBy,
        String resolution,
        String punishmentId
    ) {
        return resolveMinecraftReport(server, ticketId, new ResolveReportRequest(resolvedBy, resolution, punishmentId));
    }

    public ReportOperationResult assignMinecraftReport(Server server, String ticketId, AssignReportRequest request) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return new ReportOperationResult(ReportOperationStatus.NOT_FOUND, null);
        }
        ticket.setAssignedTo("none".equalsIgnoreCase(request.assignee())
                             ? List.of()
                             : TicketAssigneeUtil.normalizeCsv(request.assignee()));
        ticket.setUpdatedAt(new Date());
        Ticket saved = ticketRepository.saveEntity(server, ticket);
        return new ReportOperationResult(ReportOperationStatus.SUCCESS, saved);
    }

    public ReportOperationResult assignMinecraftReport(Server server, String ticketId, String assignee) {
        return assignMinecraftReport(server, ticketId, new AssignReportRequest(assignee));
    }

    public MinecraftTicketListItemView toTicketListItem(Ticket ticket) {
        boolean hasStaffResponse = ticket.getReplies() != null
            && ticket.getReplies().stream().anyMatch(TicketReply::isStaff);

        return new MinecraftTicketListItemView(
            ticket.getId(),
            ticket.getType() != null ? ticket.getType().getId() : null,
            ticket.getType() != null ? ticket.getType().getId() : null,
            ticket.getSubject(),
            ticket.getStatus() != null ? ticket.getStatus().getId() : null,
            ticket.getCreatorName(),
            ticket.getCreatorUuid(),
            ticket.getPriority() != null ? ticket.getPriority().getId() : null,
            ticket.getAssignedTo(),
            ticket.getCreated(),
            ticket.getUpdatedAt(),
            hasStaffResponse,
            ticket.getReplies() != null ? ticket.getReplies().size() : 0,
            ticket.isLocked()
        );
    }

    public MinecraftTicketDetailView toTicketDetail(Ticket ticket) {
        List<MinecraftTicketDetailReplyView> replies = new ArrayList<>();
        if (ticket.getReplies() != null) {
            for (TicketReply reply : ticket.getReplies()) {
                replies.add(new MinecraftTicketDetailReplyView(
                    reply.getId(),
                    reply.getContent(),
                    reply.getName(),
                    reply.getCreatorIdentifier(),
                    reply.isStaff(),
                    reply.getCreated()
                ));
            }
        }

        return new MinecraftTicketDetailView(
            ticket.getId(),
            ticket.getType() != null ? ticket.getType().getId() : null,
            ticket.getType() != null ? ticket.getType().getId() : null,
            ticket.getSubject(),
            ticket.getStatus() != null ? ticket.getStatus().getId() : null,
            ticket.getCreatorName(),
            ticket.getCreatorUuid(),
            ticket.getPriority() != null ? ticket.getPriority().getId() : null,
            ticket.getAssignedTo(),
            ticket.getCreated(),
            ticket.getUpdatedAt(),
            ticket.isLocked(),
            replies,
            ticket.getChatMessages(),
            ticket.getReplayUrl()
        );
    }

    public MinecraftTicketLookupView toTicketLookupItem(Ticket ticket) {
        String firstReplyContent = ticket.getReplies() != null && !ticket.getReplies().isEmpty()
            ? ticket.getReplies().get(0).getContent()
            : null;

        return new MinecraftTicketLookupView(
            ticket.getId(),
            ticket.getType() != null ? ticket.getType().getId() : null,
            ticket.getType() != null ? ticket.getType().getId() : null,
            ticket.getSubject(),
            ticket.getStatus() != null ? ticket.getStatus().getId() : null,
            ticket.getCreatorName(),
            ticket.getCreatorUuid(),
            ticket.getCreated(),
            firstReplyContent
        );
    }

    public MinecraftPlayerTicketView toPlayerTicketItem(Ticket ticket) {
        return new MinecraftPlayerTicketView(
            ticket.getId(),
            ticket.getType() != null ? ticket.getType().getId() : null,
            ticket.getType() != null ? ticket.getType().getId() : null,
            ticket.getSubject(),
            ticket.getStatus() != null ? ticket.getStatus().getId() : null,
            ticket.getCreated()
        );
    }

    public enum MinecraftTicketClaimStatus {
        SUCCESS,
        NOT_FOUND,
        ALREADY_LINKED
    }

    public enum ReportOperationStatus {
        SUCCESS,
        NOT_FOUND
    }

    public record MinecraftTicketClaimResult(MinecraftTicketClaimStatus status, Ticket ticket) {}

    public record ReportOperationResult(ReportOperationStatus status, Ticket ticket) {}

    private static Ticket.ChatMessage parseChatMessage(String rawMessage, Date reportTime) {
        String truncated = rawMessage.substring(0, Math.min(rawMessage.length(), TicketContentService.MAX_CHAT_MESSAGE_LENGTH));
        Matcher matcher = CHAT_LINE_PATTERN.matcher(truncated);
        if (matcher.matches()) {
            return Ticket.ChatMessage.builder()
                .content(matcher.group(3))
                .timestamp(reconstructTimestamp(matcher.group(1), reportTime))
                .sender(matcher.group(2).trim())
                .build();
        }
        return Ticket.ChatMessage.builder()
            .content(truncated)
            .timestamp(reportTime)
            .build();
    }

    static Date reconstructTimestamp(String timeOfDay, Date reportTime) {
        if (timeOfDay == null) {
            return reportTime;
        }
        try {
            LocalTime messageTime = LocalTime.parse(timeOfDay, CHAT_TIME_FORMAT);
            OffsetDateTime report = reportTime.toInstant().atOffset(ZoneOffset.UTC);
            OffsetDateTime messageMoment = report.with(messageTime);
            if (messageMoment.isAfter(report)) {
                messageMoment = messageMoment.minusDays(1);
            }
            return Date.from(messageMoment.toInstant());
        } catch (DateTimeParseException e) {
            return reportTime;
        }
    }
}
