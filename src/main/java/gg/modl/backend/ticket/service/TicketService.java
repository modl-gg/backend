package gg.modl.backend.ticket.service;

import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.QuickResponseSettings;
import gg.modl.backend.settings.service.QuickResponseSettingsService;
import gg.modl.backend.ticket.data.AppealWorkflowStatus;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketNote;
import gg.modl.backend.ticket.data.TicketPriority;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import gg.modl.backend.ticket.dto.request.*;
import gg.modl.backend.ticket.dto.response.PaginatedTicketsResponse;
import gg.modl.backend.ticket.dto.response.TicketListItemResponse;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.dto.response.QuickResponseResult;
import gg.modl.backend.ticket.util.TicketAssigneeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {
    private static final int MAX_MINECRAFT_CHAT_MESSAGE_LENGTH = 256;

    private final TicketMongoRepository ticketRepository;
    private final QuickResponseSettingsService quickResponseSettingsService;
    private final TicketNotificationService notificationService;

    private static final SecureRandom RANDOM = new SecureRandom();

    public Ticket createMinecraftTicket(Server server, MinecraftCreateTicketRequest request) {
        return createMinecraftTicket(server, request, false);
    }

    public Ticket createUnfinishedMinecraftTicket(Server server, MinecraftCreateTicketRequest request) {
        return createMinecraftTicket(server, request, true);
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

    public PaginatedTicketsResponse searchTickets(Server server, int page, int limit, String search, String status, String type) {
        List<String> types = type != null && !type.isBlank() ? List.of(type) : null;
        return searchTickets(server, page, limit, search, status, types, null, null, null, "newest");
    }

    public PaginatedTicketsResponse searchTickets(Server server, int page, int limit, String search, String status, List<String> types,
                                                   String author, List<String> labels, List<String> assignees, String sort) {
        TicketMongoRepository.TicketSearchFilter filter = new TicketMongoRepository.TicketSearchFilter(
                search,
                status,
                types,
                author,
                labels,
                assignees
        );
        TicketMongoRepository.TicketSearchPage searchPage = ticketRepository.searchTickets(
                server,
                filter,
                TicketMongoRepository.TicketSortOption.from(sort),
                page,
                limit
        );

        List<TicketListItemResponse> ticketItems = searchPage.tickets().stream()
                .map(this::toListItemResponse)
                .toList();

        int totalPages = (int) Math.ceil((double) searchPage.total() / limit);

        return new PaginatedTicketsResponse(
                ticketItems,
                new PaginatedTicketsResponse.PaginationInfo(
                        page,
                        totalPages,
                        limit,
                        searchPage.total(),
                        page < totalPages,
                        page > 1
                ),
                new PaginatedTicketsResponse.FiltersInfo(search, status, types)
        );
    }

    public Map<String, Long> getTicketCounts(Server server, String search, List<String> types, String author, List<String> labels, List<String> assignees) {
        TicketMongoRepository.TicketCounts ticketCounts = ticketRepository.countTickets(
                server,
                new TicketMongoRepository.TicketSearchFilter(search, null, types, author, labels, assignees)
        );

        Map<String, Long> result = new HashMap<>();
        result.put("open", ticketCounts.open());
        result.put("closed", ticketCounts.closed());
        return result;
    }

    public int bulkUpdateTickets(Server server, BulkTicketUpdateRequest request, String staffEmail) {
        int updatedCount = 0;

        for (String ticketId : request.ticketIds()) {
            Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
            if (ticket == null) {
                continue;
            }
            Date now = new Date();
            ticket.setUpdatedAt(now);
            boolean hasChanges = false;
            boolean wasClosed = ticket.getStatus() != null && ticket.getStatus().isTerminal();

            // Update locked status
            if (request.locked() != null) {
                TicketStatus nextStatus = request.locked()
                        ? TicketStatus.CLOSED
                        : (ticket.getStatus() == TicketStatus.UNFINISHED ? TicketStatus.UNFINISHED : TicketStatus.OPEN);
                applyLifecycleStatus(ticket, nextStatus);
                hasChanges = true;
            }

            // Update hidden status
            if (request.hidden() != null) {
                ticket.setHidden(request.hidden());
                hasChanges = true;
            }

            // Add labels (tags)
            if (request.addLabels() != null && !request.addLabels().isEmpty()) {
                List<String> currentTags = ticket.getTags() != null ? new ArrayList<>(ticket.getTags()) : new ArrayList<>();
                for (String label : request.addLabels()) {
                    if (!currentTags.contains(label)) {
                        currentTags.add(label);
                    }
                }
                ticket.setTags(currentTags);
                hasChanges = true;
            }

            // Remove labels (tags)
            if (request.removeLabels() != null && !request.removeLabels().isEmpty()) {
                List<String> currentTags = ticket.getTags() != null ? new ArrayList<>(ticket.getTags()) : new ArrayList<>();
                currentTags.removeAll(request.removeLabels());
                ticket.setTags(currentTags);
                hasChanges = true;
            }

            // Update assignee
            if (request.assignTo() != null) {
                List<String> assignees = "none".equalsIgnoreCase(request.assignTo())
                        ? List.of()
                        : TicketAssigneeUtil.normalizeCsv(request.assignTo());
                ticket.setAssignedTo(assignees);
                hasChanges = true;
            }

            if (hasChanges) {
                Ticket saved = ticketRepository.saveEntity(server, ticket);
                updatedCount++;

                if (!wasClosed && saved.getStatus() != null && saved.getStatus().isTerminal()) {
                    notificationService.notifyTicketClosed(server, saved);
                }
            }
        }

        return updatedCount;
    }

    public Optional<TicketResponse> getTicketById(Server server, String ticketId) {
        return ticketRepository.findById(server, ticketId).map(this::toTicketResponse);
    }

    public Optional<Ticket> getTicketRaw(Server server, String ticketId) {
        return ticketRepository.findById(server, ticketId);
    }

    public TicketResponse createTicket(Server server, CreateTicketRequest request) {
        TicketCategory ticketCategory = TicketCategory.fromCanonicalId(request.type());
        String ticketId = generateTicketId(server, ticketCategory);

        boolean shouldOpenImmediately = ticketCategory.isReport()
                || (request.subject() != null && !request.subject().isBlank());
        TicketStatus ticketStatus = shouldOpenImmediately ? TicketStatus.OPEN : TicketStatus.UNFINISHED;
        String subject = (request.subject() != null && !request.subject().isBlank())
                ? request.subject()
                : ticketCategory.getDisplayName();

        List<String> tags = request.tags() != null ? new ArrayList<>(request.tags()) : new ArrayList<>();

        Map<String, Object> data = new HashMap<>();
        String creatorEmail = EmailAddressUtil.normalizeIfValid(request.creatorEmail());
        if (creatorEmail != null) {
            data.put("creatorEmail", creatorEmail);
        }
        if (request.creatorIdentifier() != null) {
            data.put("creatorIdentifier", request.creatorIdentifier());
        }

        FormDataProcessingResult createFormDataProcessing = processFormDataForContent(request.formData());
        List<Object> initialAttachments = mergeAttachments(
                normalizeAttachments(request.attachments()),
                createFormDataProcessing.attachments()
        );

        List<TicketReply> replies = new ArrayList<>();
        String content = buildTicketContent(request, createFormDataProcessing.formData());
        if (!content.isBlank() || !initialAttachments.isEmpty()) {
            TicketReply initialReply = TicketReply.builder()
                    .id(UUID.randomUUID().toString())
                    .name(request.creatorName() != null ? request.creatorName() : "API User")
                    .content(content)
                    .type("user")
                    .created(new Date())
                    .staff(false)
                    .attachments(initialAttachments)
                    .creatorIdentifier(request.creatorIdentifier())
                    .build();
            replies.add(initialReply);
        }

        String creatorDisplayName = request.creatorName() != null ? request.creatorName() : "API User";

        boolean emailAuth = Boolean.TRUE.equals(request.emailAuthEnabled());

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .type(ticketCategory)
                .subject(subject)
                .status(ticketStatus)
                .appealWorkflowStatus(ticketCategory.isAppeal() ? AppealWorkflowStatus.OPEN : null)
                .creatorName(creatorDisplayName)
                .creatorUuid(request.creatorUuid())
                .reportedPlayer(request.reportedPlayerName())
                .reportedPlayerUuid(request.reportedPlayerUuid())
                .tags(tags)
                .replies(replies)
                .notes(new ArrayList<>())
                .chatMessages(request.chatMessages() == null || request.chatMessages().isEmpty() ? null : sanitizeChatMessages(request.chatMessages()))
                .formData(request.formData())
                .data(data)
                .locked(ticketStatus.isTerminal())
                .priority(resolvePriority(request.priority()))
                .emailAuthEnabled(emailAuth)
                .created(new Date())
                .updatedAt(new Date())
                .build();

        ticketRepository.saveEntity(server, ticket);

        return toTicketResponse(ticket);
    }

    public Optional<TicketResponse> updateTicket(Server server, String ticketId, UpdateTicketRequest request, String staffEmail) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return Optional.empty();
        }
        boolean wasClosed = ticket.getStatus() != null && ticket.getStatus().isTerminal();
        ticket.setUpdatedAt(new Date());

        if (request.status() != null) {
            applyLifecycleStatus(ticket, TicketStatus.fromCanonicalId(request.status()));
        }

        if (request.locked() != null) {
            TicketStatus nextStatus = request.locked()
                    ? TicketStatus.CLOSED
                    : (ticket.getStatus() == TicketStatus.UNFINISHED ? TicketStatus.UNFINISHED : TicketStatus.OPEN);
            applyLifecycleStatus(ticket, nextStatus);
        }

        if (request.tags() != null) {
            ticket.setTags(request.tags());
        }

        if (request.hidden() != null) {
            ticket.setHidden(request.hidden());
        }

        if (request.data() != null && !request.data().isEmpty()) {
            Map<String, Object> existingData = ticket.getData() != null ? new HashMap<>(ticket.getData()) : new HashMap<>();
            existingData.putAll(request.data());
            ticket.setData(existingData);
        }

        TicketReply newReply = null;
        if (request.newReply() != null) {
            newReply = TicketReply.builder()
                    .id(UUID.randomUUID().toString())
                    .name(request.newReply().name())
                    .avatar(request.newReply().avatar())
                    .content(request.newReply().content())
                    .type(request.newReply().type() != null ? request.newReply().type() : "public")
                    .created(new Date())
                    .staff(request.newReply().staff())
                    .action(request.newReply().action())
                    .attachments(request.newReply().attachments() != null ? request.newReply().attachments() : new ArrayList<>())
                    .build();

            ensureTicketReplies(ticket).add(newReply);
        }

        if (request.newNote() != null) {
            TicketNote newNote = TicketNote.builder()
                    .text(request.newNote().text())
                    .issuerName(request.newNote().issuerName())
                    .issuerAvatar(request.newNote().issuerAvatar())
                    .date(new Date())
                    .build();

            ensureTicketNotes(ticket).add(newNote);
        }

        Ticket saved = ticketRepository.saveEntity(server, ticket);

        // Send notifications for staff replies
        if (newReply != null && newReply.isStaff()) {
            notificationService.notifyTicketReply(server, saved, newReply);
        }

        if (!wasClosed && saved.getStatus() != null && saved.getStatus().isTerminal()) {
            notificationService.notifyTicketClosed(server, saved);
        }

        return Optional.of(toTicketResponse(saved));
    }

    public Optional<TicketReply> addReply(Server server, String ticketId, AddReplyRequest request) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return Optional.empty();
        }

        if (ticket.isLocked()) {
            throw new IllegalStateException("Ticket is locked and cannot accept new replies");
        }

        TicketReply newReply = TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .avatar(request.avatar())
                .content(request.content())
                .type(request.type() != null ? request.type() : "public")
                .created(new Date())
                .staff(request.staff())
                .action(request.action())
                .attachments(request.attachments() != null ? request.attachments() : new ArrayList<>())
                .creatorIdentifier(request.creatorIdentifier())
                .build();
        ensureTicketReplies(ticket).add(newReply);
        ticket.setUpdatedAt(new Date());
        Ticket saved = ticketRepository.saveEntity(server, ticket);

        // Send notifications for staff replies
        if (request.staff()) {
            notificationService.notifyTicketReply(server, saved, newReply);
        }

        return Optional.of(newReply);
    }

    public Optional<TicketNote> addNote(Server server, String ticketId, AddNoteRequest request) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return Optional.empty();
        }

        TicketNote newNote = TicketNote.builder()
                .text(request.text())
                .issuerName(request.issuerName())
                .issuerAvatar(request.issuerAvatar())
                .date(new Date())
                .build();
        ensureTicketNotes(ticket).add(newNote);
        ticket.setUpdatedAt(new Date());
        ticketRepository.saveEntity(server, ticket);

        return Optional.of(newNote);
    }

    public Optional<List<String>> addTag(Server server, String ticketId, String tag) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return Optional.empty();
        }

        List<String> tags = ticket.getTags() != null ? new ArrayList<>(ticket.getTags()) : new ArrayList<>();
        if (!tags.contains(tag)) {
            tags.add(tag);
            ticket.setTags(tags);
            ticket.setUpdatedAt(new Date());
            ticketRepository.saveEntity(server, ticket);
        }

        return Optional.of(tags);
    }

    public Optional<List<String>> removeTag(Server server, String ticketId, String tag) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return Optional.empty();
        }

        List<String> tags = ticket.getTags() != null ? new ArrayList<>(ticket.getTags()) : new ArrayList<>();
        if (tags.remove(tag)) {
            ticket.setTags(tags);
            ticket.setUpdatedAt(new Date());
            ticketRepository.saveEntity(server, ticket);
        }

        return Optional.of(tags);
    }

    public List<Ticket> getTicketsByPlayer(Server server, String playerUuid) {
        return ticketRepository.findByPlayer(server, playerUuid);
    }

    public List<Ticket> getTicketsByTag(Server server, String tag) {
        return ticketRepository.findByTag(server, tag);
    }

    public QuickResponseResult processQuickResponse(Server server, String ticketId, QuickResponseRequest request, String staffUsername) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return new QuickResponseResult(false, "Ticket not found", null, null, false, false, null);
        }

        QuickResponseSettings settings = quickResponseSettingsService.getQuickResponseSettings(server);
        if (settings == null) {
            return new QuickResponseResult(false, "Quick response settings not found", ticketId, null, false, false, null);
        }

        QuickResponseSettings.Action action = quickResponseSettingsService.findAction(settings, request.categoryId(), request.actionId());
        if (action == null) {
            return new QuickResponseResult(false, "Action not found", ticketId, null, false, false, null);
        }

        TicketReply responseReply = TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(staffUsername != null ? staffUsername : "System")
                .content(action.getMessage())
                .type("public")
                .created(new Date())
                .staff(true)
                .build();
        ensureTicketReplies(ticket).add(responseReply);
        ticket.setUpdatedAt(new Date());
        boolean ticketClosed = false;
        if (Boolean.TRUE.equals(action.getCloseTicket())) {
            applyLifecycleStatus(ticket, TicketStatus.CLOSED);
            ticketClosed = true;
        }

        Ticket saved = ticketRepository.saveEntity(server, ticket);

        // Send notifications for the quick response (which is a staff reply)
        notificationService.notifyTicketReply(server, saved, responseReply);

        // Send transcript email if ticket was closed
        if (ticketClosed) {
            notificationService.notifyTicketClosed(server, saved);
        }

        return new QuickResponseResult(
                true,
                "Quick response applied successfully",
                ticketId,
                action.getName(),
                ticketClosed,
                false,
                action.getAppealAction()
        );
    }

    public Optional<TicketResponse> submitTicketForm(Server server, String ticketId, SubmitTicketFormRequest request) {
        Ticket ticket = ticketRepository.findById(server, ticketId).orElse(null);
        if (ticket == null) {
            return Optional.empty();
        }
        applyLifecycleStatus(ticket, TicketStatus.OPEN);
        ticket.setUpdatedAt(new Date());

        Map<String, Object> requestFormData = request.formData() != null ? request.formData() : Collections.emptyMap();
        FormDataProcessingResult formDataProcessing = processFormDataForContent(requestFormData);
        List<Object> initialAttachments = mergeAttachments(
                normalizeAttachments(request.attachments()),
                formDataProcessing.attachments()
        );

        if (request.subject() != null) {
            ticket.setSubject(request.subject());
        }

        Map<String, Object> existingData = ticket.getData() != null ? new HashMap<>(ticket.getData()) : new HashMap<>();
        boolean hasDataUpdates = false;

        if (request.formData() != null && !request.formData().isEmpty()) {
            existingData.putAll(sanitizeFormDataForDataStore(request.formData()));
            hasDataUpdates = true;

            // Handle emailAuthEnabled from form data.
            Object emailAuthValue = request.formData().get("emailAuthEnabled");
            if (emailAuthValue != null) {
                boolean emailAuth = Boolean.parseBoolean(emailAuthValue.toString());
                ticket.setEmailAuthEnabled(emailAuth);
                existingData.put("emailAuthEnabled", emailAuth);
            }

            ticket.setFormData(request.formData());
        }

        String creatorEmail = resolveCreatorEmail(request);
        if (creatorEmail != null) {
            existingData.put("creatorEmail", creatorEmail);
            hasDataUpdates = true;
        }

        if (request.creatorIdentifier() != null) {
            existingData.put("creatorIdentifier", request.creatorIdentifier());
            hasDataUpdates = true;
        }

        if (hasDataUpdates) {
            ticket.setData(existingData);
        }

        // Create initial reply from submitted form data/attachments (only if no replies exist yet)
        if (ticket.getReplies() == null || ticket.getReplies().isEmpty()) {
            String content = buildFormDataContent(formDataProcessing.formData());
            if (!content.isBlank() || !initialAttachments.isEmpty()) {
                TicketReply initialReply = TicketReply.builder()
                        .id(UUID.randomUUID().toString())
                        .name(ticket.getCreatorName() != null ? ticket.getCreatorName() : "Player")
                        .content(content)
                        .type("user")
                        .created(new Date())
                        .staff(false)
                        .attachments(initialAttachments)
                        .creatorIdentifier(request.creatorIdentifier())
                        .build();
                ensureTicketReplies(ticket).add(initialReply);
            }
        }

        Ticket saved = ticketRepository.saveEntity(server, ticket);

        return Optional.of(toTicketResponse(saved));
    }

    private String resolveCreatorEmail(SubmitTicketFormRequest request) {
        String explicitCreatorEmail = EmailAddressUtil.normalizeIfValid(request.creatorEmail());
        if (explicitCreatorEmail != null) {
            return explicitCreatorEmail;
        }

        if (request.formData() == null || request.formData().isEmpty()) {
            return null;
        }

        Object legacyEmail = request.formData().containsKey("contact_email")
                ? request.formData().get("contact_email")
                : request.formData().get("email");
        if (legacyEmail == null) {
            return null;
        }

        return EmailAddressUtil.normalizeIfValid(legacyEmail.toString());
    }

    private Map<String, Object> sanitizeFormDataForDataStore(Map<String, Object> formData) {
        Map<String, Object> sanitized = new LinkedHashMap<>(formData);
        sanitized.remove("creatorEmail");
        sanitized.remove("creatorIdentifier");
        return sanitized;
    }

    /**
     * Builds content from form data for the initial ticket message.
     */
    private String buildFormDataContent(Map<String, Object> formData) {
        if (formData == null || formData.isEmpty()) {
            return "";
        }

        StringBuilder content = new StringBuilder();
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().toString().isBlank()) {
                String formattedKey = formatFormDataKey(entry.getKey());
                content.append("**").append(formattedKey).append(":** ").append(entry.getValue()).append("\n\n");
            }
        }
        return content.toString().trim();
    }

    private FormDataProcessingResult processFormDataForContent(Map<String, Object> formData) {
        if (formData == null || formData.isEmpty()) {
            return new FormDataProcessingResult(Collections.emptyMap(), new ArrayList<>());
        }

        Map<String, Object> sanitizedFormData = new LinkedHashMap<>();
        List<Object> extractedAttachments = new ArrayList<>();

        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            if (value instanceof String textValue) {
                String trimmedValue = textValue.trim();
                if (trimmedValue.isBlank()) {
                    continue;
                }

                if (isLikelyAttachmentField(entry.getKey(), trimmedValue)) {
                    extractedAttachments.add(createAttachmentFromUrl(trimmedValue));
                    continue;
                }
            }

            sanitizedFormData.put(entry.getKey(), value);
        }

        return new FormDataProcessingResult(sanitizedFormData, extractedAttachments);
    }

    private List<Object> normalizeAttachments(List<Object> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new ArrayList<>();
        }

        List<Object> normalized = new ArrayList<>();

        for (Object attachment : attachments) {
            if (attachment == null) {
                continue;
            }

            if (attachment instanceof String attachmentUrl) {
                String trimmedUrl = attachmentUrl.trim();
                if (trimmedUrl.isBlank()) {
                    continue;
                }
                normalized.add(createAttachmentFromUrl(trimmedUrl));
                continue;
            }

            if (attachment instanceof Map<?, ?> mapAttachment) {
                Map<String, Object> normalizedMap = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : mapAttachment.entrySet()) {
                    if (entry.getKey() != null) {
                        normalizedMap.put(entry.getKey().toString(), entry.getValue());
                    }
                }

                Object urlValue = normalizedMap.get("url");
                if (urlValue == null || urlValue.toString().isBlank()) {
                    continue;
                }

                String url = urlValue.toString().trim();
                normalizedMap.put("url", url);
                normalizedMap.putIfAbsent("fileName", extractFileName(url));
                normalizedMap.putIfAbsent("fileType", inferFileType(url));
                normalizedMap.putIfAbsent("fileSize", 0);
                normalized.add(normalizedMap);
                continue;
            }

            normalized.add(attachment);
        }

        return dedupeAttachments(normalized);
    }

    private List<Object> mergeAttachments(List<Object> explicitAttachments, List<Object> inferredAttachments) {
        List<Object> merged = new ArrayList<>();
        if (explicitAttachments != null && !explicitAttachments.isEmpty()) {
            merged.addAll(explicitAttachments);
        }
        if (inferredAttachments != null && !inferredAttachments.isEmpty()) {
            merged.addAll(inferredAttachments);
        }
        return dedupeAttachments(merged);
    }

    private List<Object> dedupeAttachments(List<Object> attachments) {
        List<Object> deduped = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        for (Object attachment : attachments) {
            String url = extractAttachmentUrl(attachment);
            if (url != null) {
                String normalizedUrl = url.trim();
                if (normalizedUrl.isBlank() || !seenUrls.add(normalizedUrl)) {
                    continue;
                }
            }
            deduped.add(attachment);
        }

        return deduped;
    }

    private String extractAttachmentUrl(Object attachment) {
        if (attachment instanceof String attachmentUrl) {
            return attachmentUrl;
        }
        if (attachment instanceof Map<?, ?> mapAttachment) {
            Object url = mapAttachment.get("url");
            return url != null ? url.toString() : null;
        }
        return null;
    }

    private boolean isLikelyAttachmentField(String key, String value) {
        if (key == null || key.isBlank() || !isHttpUrl(value)) {
            return false;
        }

        String normalizedKey = key
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .toLowerCase(Locale.ROOT);

        for (String token : normalizedKey.split("\\s+")) {
            if (token.equals("attachment") || token.equals("attachments")
                    || token.equals("upload") || token.equals("uploads")
                    || token.equals("file") || token.equals("files")) {
                return true;
            }
        }
        return false;
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("https://") || value.startsWith("http://");
    }

    private Map<String, Object> createAttachmentFromUrl(String url) {
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("url", url);
        attachment.put("fileName", extractFileName(url));
        attachment.put("fileType", inferFileType(url));
        attachment.put("fileSize", 0);
        return attachment;
    }

    private String extractFileName(String url) {
        if (url == null || url.isBlank()) {
            return "attachment";
        }

        String cleanedUrl = url.split("\\?")[0];
        int lastSlashIndex = cleanedUrl.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < cleanedUrl.length() - 1) {
            return cleanedUrl.substring(lastSlashIndex + 1);
        }
        return "attachment";
    }

    private String inferFileType(String url) {
        String fileName = extractFileName(url).toLowerCase(Locale.ROOT);
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "application/octet-stream";
        }

        String extension = fileName.substring(dotIndex + 1);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "mkv" -> "video/x-matroska";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private Ticket createMinecraftTicket(Server server, MinecraftCreateTicketRequest request, boolean unfinished) {
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
            int randomId = 100000 + RANDOM.nextInt(900000);
            ticketId = prefix + "-" + randomId;
            attempts++;
        } while (ticketRepository.existsByTicketId(server, ticketId) && attempts < 10);

        return ticketId;
    }

    public record MinecraftTicketClaimResult(MinecraftTicketClaimStatus status, Ticket ticket) {
    }

    public record ReportOperationResult(ReportOperationStatus status, Ticket ticket) {
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

    private String buildTicketContent(CreateTicketRequest request, Map<String, Object> formDataForContent) {
        StringBuilder content = new StringBuilder();

        if (request.description() != null && !request.description().isBlank()) {
            content.append("**Description:** ").append(request.description()).append("\n\n");
        }

        if (request.chatMessages() != null && !request.chatMessages().isEmpty()) {
            content.append("**Chat Messages:**\n");
            for (Map<String, Object> msg : request.chatMessages()) {
                if (msg.containsKey("username") && msg.containsKey("message")) {
                    String timestamp = msg.containsKey("timestamp") ? msg.get("timestamp").toString() : "Unknown time";
                    content.append(String.format("`[%s]` **%s**: %s\n", timestamp, msg.get("username"), msg.get("message")));
                }
            }
            content.append("\n");
        }

        if (formDataForContent != null && !formDataForContent.isEmpty()) {
            for (Map.Entry<String, Object> entry : formDataForContent.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().toString().isBlank()) {
                    String formattedKey = formatFormDataKey(entry.getKey());
                    content.append("**").append(formattedKey).append(":** ").append(entry.getValue()).append("\n\n");
                }
            }
        }

        return content.toString().trim();
    }

    private record FormDataProcessingResult(Map<String, Object> formData, List<Object> attachments) {}

    /**
     * Formats a form data key into a human-readable title.
     * Converts snake_case or camelCase to Title Case with spaces.
     * Example: "contact_email" -> "Contact Email", "bugDescription" -> "Bug Description"
     */
    private String formatFormDataKey(String key) {
        if (key == null || key.isBlank()) {
            return key;
        }

        // Replace underscores with spaces
        String formatted = key.replace("_", " ");

        // Insert spaces before uppercase letters (for camelCase)
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isWhitespace(formatted.charAt(i - 1))) {
                result.append(' ');
            }
            result.append(c);
        }
        formatted = result.toString();

        // Capitalize first letter of each word (Title Case)
        String[] words = formatted.split("\\s+");
        StringBuilder titleCase = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                titleCase.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    titleCase.append(word.substring(1).toLowerCase());
                }
                if (i < words.length - 1) {
                    titleCase.append(" ");
                }
            }
        }

        return titleCase.toString();
    }

    private TicketListItemResponse toListItemResponse(Ticket ticket) {
        TicketReply lastReply = null;
        int replyCount = 0;
        String creatorName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Unknown";

        if (ticket.getReplies() != null && !ticket.getReplies().isEmpty()) {
            replyCount = ticket.getReplies().size();
            lastReply = ticket.getReplies().get(replyCount - 1);
        }

        return new TicketListItemResponse(
                ticket.getId(),
                ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
                ticket.getStatus() != null ? ticket.getStatus().getId() : TicketStatus.OPEN.getId(),
                creatorName,
                creatorName,
                ticket.getCreated(),
                ticket.getType() != null ? ticket.getType().getDisplayName() : TicketCategory.SUPPORT.getDisplayName(),
                ticket.isLocked(),
                ticket.getType() != null ? ticket.getType().getId() : TicketCategory.SUPPORT.getId(),
                lastReply,
                replyCount,
                ticket.getTags() != null ? ticket.getTags() : new ArrayList<>(),
                ticket.getAssignedTo() != null ? ticket.getAssignedTo() : List.of(),
                ticket.isHidden()
        );
    }

    private TicketResponse toTicketResponse(Ticket ticket) {
        // Ensure all replies have proper names
        List<TicketReply> processedReplies = processRepliesWithNames(ticket);
        String creatorName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Unknown";

        return new TicketResponse(
                ticket.getId(),
                ticket.getType() != null ? ticket.getType().getId() : TicketCategory.SUPPORT.getId(),
                ticket.getType() != null ? ticket.getType().getDisplayName() : TicketCategory.SUPPORT.getDisplayName(),
                ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
                ticket.getStatus() != null ? ticket.getStatus().getId() : TicketStatus.OPEN.getId(),
                ticket.getAppealWorkflowStatus() != null ? ticket.getAppealWorkflowStatus().getId() : null,
                creatorName,
                ticket.getCreatorUuid(),
                creatorName,
                ticket.getReportedPlayer(),
                ticket.getReportedPlayerUuid(),
                ticket.getCreated(),
                ticket.isLocked(),
                processedReplies,
                ticket.getNotes(),
                ticket.getTags(),
                ticket.getFormData(),
                ticket.getData(),
                ticket.getChatMessages(),
                ticket.getAiAnalysis(),
                ticket.isEmailAuthEnabled(),
                ticket.isHidden()
        );
    }

    private List<TicketReply> processRepliesWithNames(Ticket ticket) {
        if (ticket.getReplies() == null || ticket.getReplies().isEmpty()) {
            return ticket.getReplies();
        }

        String creatorName = ticket.getCreatorName() != null ? ticket.getCreatorName() : "Player";

        return ticket.getReplies().stream().map(reply -> {
            if (reply.getName() == null || reply.getName().isBlank()) {
                // Set fallback name based on whether it's staff or user
                String fallbackName = reply.isStaff() ? "Staff" : creatorName;
                reply.setName(fallbackName);
            }
            // Ensure type is set
            if (reply.getType() == null || reply.getType().isBlank()) {
                reply.setType(reply.isStaff() ? "staff" : "user");
            }
            return reply;
        }).toList();
    }

    private static final int MAX_CHAT_MESSAGE_LENGTH = 256;
    private static final int MAX_CHAT_MESSAGES = 50;

    private List<Ticket.ChatMessage> sanitizeChatMessages(List<Map<String, Object>> rawMessages) {
        // Keep only the last MAX_CHAT_MESSAGES messages
        List<Map<String, Object>> trimmed = rawMessages.size() > MAX_CHAT_MESSAGES
                ? rawMessages.subList(rawMessages.size() - MAX_CHAT_MESSAGES, rawMessages.size())
                : rawMessages;

        return trimmed.stream()
                .map(x -> {
                    String content = (String) x.get("content");
                    if (content != null && content.length() > MAX_CHAT_MESSAGE_LENGTH) {
                        content = content.substring(0, MAX_CHAT_MESSAGE_LENGTH);
                    }
                    return new Ticket.ChatMessage(content, parseTimestamp(x.get("timestamp")));
                })
                .toList();
    }

    private static Date parseTimestamp(Object value) {
        if (value instanceof Date date) return date;
        if (value instanceof String str) return Date.from(Instant.parse(str));
        return new Date();
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

    private List<TicketNote> ensureTicketNotes(Ticket ticket) {
        if (ticket.getNotes() == null) {
            ticket.setNotes(new ArrayList<>());
        }
        return ticket.getNotes();
    }

    private Map<String, Object> ensureTicketData(Ticket ticket) {
        if (ticket.getData() == null) {
            ticket.setData(new HashMap<>());
        }
        return ticket.getData();
    }
}

