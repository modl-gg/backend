package gg.modl.backend.ticket.service;

import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
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
import gg.modl.backend.ticket.util.TicketAssigneeUtil;
import gg.modl.backend.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinecraftTicketService {
    private static final int MAX_MINECRAFT_CHAT_MESSAGE_LENGTH = 256;

    private final TicketMongoRepository ticketRepository;
    private final TicketNotificationService notificationService;
    private final IdGenerator idGenerator;

    public Ticket createMinecraftTicket(Server server, MinecraftCreateTicketRequest request) {
        return createMinecraftTicketInternal(server, request, false);
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
        return ticketRepository.findRecentByCreator(server, creatorUuid, limit);
    }

    public MinecraftTicketClaimResult claimMinecraftTicket(Server server, String ticketId, MinecraftClaimTicketRequest request) {
        Optional<Ticket> existingTicket = ticketRepository.findById(server, ticketId);
        if (existingTicket.isEmpty()) {
            return new MinecraftTicketClaimResult(MinecraftTicketClaimStatus.NOT_FOUND, null);
        }

        Ticket ticket = existingTicket.get();
        if (ticket.getCreatorUuid() != null && !ticket.getCreatorUuid().isBlank()) {
            return new MinecraftTicketClaimResult(MinecraftTicketClaimStatus.ALREADY_LINKED, ticket);
        }
        String oldCreatorName = ticket.getCreatorName();
        ticket.setCreatorUuid(request.playerUuid());
        ticket.setCreatorName(request.playerName());
        ticket.setUpdatedAt(new Date());

        if (ticket.getReplies() != null && oldCreatorName != null) {
            List<TicketReply> updatedReplies = new ArrayList<>();
            for (TicketReply reply : ticket.getReplies()) {
                if (!reply.isStaff() && oldCreatorName.equals(reply.getName())) {
                    reply.setName(request.playerName());
                }
                updatedReplies.add(reply);
            }
            ticket.setReplies(updatedReplies);
        }

        Ticket saved = ticketRepository.saveEntity(server, ticket);
        return new MinecraftTicketClaimResult(MinecraftTicketClaimStatus.SUCCESS, saved);
    }

    public List<Ticket> getMinecraftTicketsByIds(Server server, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ticketRepository.findByIds(server, ids);
    }

    public List<Map<String, Object>> getMinecraftReports(Server server, String status, int limit) {
        return ticketRepository.findReports(server, status, null, limit, true).stream()
                .map(this::toMinecraftReport)
                .toList();
    }

    public List<Map<String, Object>> getMinecraftReportsForPlayer(Server server, String playerUuid, String status, int limit) {
        return ticketRepository.findReports(server, status, playerUuid, limit, false).stream()
                .map(this::toMinecraftReport)
                .toList();
    }

    public ReportOperationResult dismissMinecraftReport(Server server, String ticketId, DismissReportRequest request) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return new ReportOperationResult(ReportOperationStatus.NOT_FOUND, null);
        }
        Date now = new Date();
        String staffName = request.dismissedBy() != null ? request.dismissedBy() : "Staff";
        TicketReply reply = TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(staffName)
                .content("Thank you for submitting this report. After careful review, we have found insufficient evidence to take action at this time.")
                .type("reply")
                .created(now)
                .staff(true)
                .action("close")
                .build();

        ensureTicketReplies(ticket).add(reply);
        applyLifecycleStatus(ticket, TicketStatus.CLOSED);
        ticket.setUpdatedAt(now);

        if (request.reason() != null && !request.reason().isBlank()) {
            ensureTicketData(ticket).put("dismissReason", request.reason());
        }
        if (request.dismissedBy() != null) {
            Map<String, Object> data = ensureTicketData(ticket);
            data.put("dismissedBy", request.dismissedBy());
            data.put("dismissedAt", now);
        }

        Ticket saved = ticketRepository.saveEntity(server, ticket);
        notificationService.notifyTicketReply(server, saved, reply);
        return new ReportOperationResult(ReportOperationStatus.SUCCESS, saved);
    }

    public ReportOperationResult resolveMinecraftReport(Server server, String ticketId, ResolveReportRequest request) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return new ReportOperationResult(ReportOperationStatus.NOT_FOUND, null);
        }
        Date now = new Date();
        String staffName = request.resolvedBy() != null ? request.resolvedBy() : "Staff";
        TicketReply reply = TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(staffName)
                .content("Thank you for creating this report. After careful review, we have accepted this and the reported player has received a punishment.")
                .type("reply")
                .created(now)
                .staff(true)
                .action("close")
                .build();

        ensureTicketReplies(ticket).add(reply);
        applyLifecycleStatus(ticket, TicketStatus.CLOSED);
        ticket.setUpdatedAt(now);

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

        Ticket saved = ticketRepository.saveEntity(server, ticket);
        notificationService.notifyTicketReply(server, saved, reply);
        return new ReportOperationResult(ReportOperationStatus.SUCCESS, saved);
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

    private Ticket createMinecraftTicketInternal(Server server, MinecraftCreateTicketRequest request, boolean unfinished) {
        TicketCategory ticketCategory = TicketCategory.fromCanonicalId(request.type());
        String ticketId = generateTicketId(server, ticketCategory);
        Date now = new Date();

        List<Ticket.ChatMessage> chatMessages = new ArrayList<>();
        if (!unfinished && request.chatMessages() != null && !request.chatMessages().isEmpty()) {
            for (String message : request.chatMessages()) {
                if (message == null || message.isBlank()) {
                    continue;
                }
                chatMessages.add(new Ticket.ChatMessage(
                        message.substring(0, Math.min(message.length(), MAX_MINECRAFT_CHAT_MESSAGE_LENGTH)),
                        now
                ));
            }
        }

        Map<String, Object> ticketData = new HashMap<>();
        if (request.createdServer() != null && !request.createdServer().isBlank()) {
            ticketData.put("createdServer", request.createdServer());
        }

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .type(ticketCategory)
                .subject(request.subject())
                .status(unfinished ? TicketStatus.UNFINISHED : TicketStatus.OPEN)
                .appealWorkflowStatus(ticketCategory.isAppeal() ? AppealWorkflowStatus.OPEN : null)
                .creatorUuid(request.creatorUuid())
                .creatorName(request.creatorName())
                .reportedPlayer(request.reportedPlayerName())
                .reportedPlayerUuid(request.reportedPlayerUuid())
                .tags(request.tags() != null ? new ArrayList<>(request.tags()) : new ArrayList<>())
                .replies(new ArrayList<>())
                .notes(new ArrayList<>())
                .chatMessages(unfinished ? null : chatMessages)
                .data(ticketData.isEmpty() ? null : ticketData)
                .priority(resolvePriority(request.priority()))
                .locked(false)
                .created(now)
                .updatedAt(now)
                .build();

        if (!unfinished && request.description() != null && !request.description().isBlank()) {
            TicketReply initialReply = TicketReply.builder()
                    .id(UUID.randomUUID().toString())
                    .content(request.description())
                    .name(request.creatorName() != null ? request.creatorName() : "Player")
                    .creatorIdentifier(request.creatorUuid())
                    .staff(false)
                    .type("user")
                    .created(now)
                    .build();
            ticket.getReplies().add(initialReply);
        }

        return ticketRepository.saveEntity(server, ticket);
    }

    private String generateTicketId(Server server, TicketCategory category) {
        String prefix = category.getTicketPrefix();
        String ticketId;
        int attempts = 0;

        do {
            int randomId = idGenerator.nextSixDigitInt();
            ticketId = prefix + "-" + randomId;
            attempts++;
        } while (ticketRepository.existsByTicketId(server, ticketId) && attempts < 10);

        return ticketId;
    }

    private Map<String, Object> toMinecraftReport(Ticket ticket) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", ticket.getId());
        report.put("type", ticket.getType() != null ? ticket.getType().getId() : null);
        report.put("reporterName", ticket.getCreatorName() != null ? ticket.getCreatorName() : "Unknown");
        report.put("reporterUuid", ticket.getCreatorUuid());
        report.put("reportedPlayerUuid", ticket.getReportedPlayerUuid());
        report.put("reportedPlayerName", ticket.getReportedPlayer());
        report.put("subject", ticket.getSubject());
        report.put("content", ticket.getReplies() != null && !ticket.getReplies().isEmpty()
                ? ticket.getReplies().get(0).getContent()
                : null);
        report.put("status", ticket.getStatus() != null ? ticket.getStatus().getId() : TicketStatus.OPEN.getId());
        report.put("priority", resolvePriority(ticket).getId());
        report.put("createdAt", ticket.getCreated());
        report.put("assignedTo", ticket.getAssignedTo());
        report.put("chatMessages", ticket.getChatMessages());
        return report;
    }

    private void applyLifecycleStatus(Ticket ticket, TicketStatus status) {
        ticket.setStatus(status);
        ticket.setLocked(status != null && status.isTerminal());
    }

    private TicketPriority resolvePriority(String priority) {
        return priority == null || priority.isBlank()
                ? TicketPriority.NORMAL
                : TicketPriority.fromCanonicalId(priority);
    }

    private TicketPriority resolvePriority(Ticket ticket) {
        return ticket.getPriority() != null ? ticket.getPriority() : TicketPriority.NORMAL;
    }

    private List<TicketReply> ensureTicketReplies(Ticket ticket) {
        if (ticket.getReplies() == null) {
            ticket.setReplies(new ArrayList<>());
        }
        return ticket.getReplies();
    }

    private Map<String, Object> ensureTicketData(Ticket ticket) {
        if (ticket.getData() == null) {
            ticket.setData(new HashMap<>());
        }
        return ticket.getData();
    }

    public record MinecraftTicketClaimResult(MinecraftTicketClaimStatus status, Ticket ticket) {}

    public record ReportOperationResult(ReportOperationStatus status, Ticket ticket) {}

    public enum MinecraftTicketClaimStatus {
        SUCCESS,
        NOT_FOUND,
        ALREADY_LINKED
    }

    public enum ReportOperationStatus {
        SUCCESS,
        NOT_FOUND
    }
}
