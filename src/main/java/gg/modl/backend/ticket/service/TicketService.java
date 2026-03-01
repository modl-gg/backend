package gg.modl.backend.ticket.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.QuickResponseSettings;
import gg.modl.backend.settings.service.QuickResponseSettingsService;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketNote;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketType;
import gg.modl.backend.ticket.dto.request.*;
import gg.modl.backend.ticket.dto.response.PaginatedTicketsResponse;
import gg.modl.backend.ticket.dto.response.TicketListItemResponse;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.dto.response.QuickResponseResult;
import gg.modl.backend.ticket.util.TicketAssigneeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final QuickResponseSettingsService quickResponseSettingsService;
    private final TicketNotificationService notificationService;

    private static final SecureRandom RANDOM = new SecureRandom();

    public PaginatedTicketsResponse searchTickets(Server server, int page, int limit, String search, String status, String type) {
        List<String> types = type != null && !type.isBlank() ? List.of(type) : null;
        return searchTickets(server, page, limit, search, status, types, null, null, null, "newest");
    }

    public PaginatedTicketsResponse searchTickets(Server server, int page, int limit, String search, String status, List<String> types,
                                                   String author, List<String> labels, List<String> assignees, String sort) {
        MongoTemplate template = getTemplate(server);

        Query query = new Query();
        query.addCriteria(Criteria.where("status").ne("Unfinished"));

        if (search != null && !search.isBlank()) {
            String escapedSearch = java.util.regex.Pattern.quote(search);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("_id").regex(escapedSearch, "i"),
                    Criteria.where("subject").regex(escapedSearch, "i"),
                    Criteria.where("creatorName").regex(escapedSearch, "i"),
                    Criteria.where("replies.name").regex(escapedSearch, "i"),
                    Criteria.where("replies.content").regex(escapedSearch, "i")
            );
            query.addCriteria(searchCriteria);
        }

        if (status != null && !status.isBlank() && !status.equals("all")) {
            if (status.equals("open")) {
                query.addCriteria(Criteria.where("locked").ne(true));
            } else if (status.equals("closed")) {
                query.addCriteria(Criteria.where("locked").is(true));
            }
        }

        // Filter by types (OR logic for multiple types)
        if (types != null && !types.isEmpty()) {
            List<String> validTypes = types.stream()
                    .filter(t -> t != null && !t.isBlank() && !t.equals("all"))
                    .toList();
            if (!validTypes.isEmpty()) {
                List<Criteria> typeCriteriaList = validTypes.stream()
                        .flatMap(type -> {
                            String escapedType = java.util.regex.Pattern.quote(type);
                            return java.util.stream.Stream.of(
                                Criteria.where("type").regex("^" + escapedType + "$", "i"),
                                Criteria.where("category").regex("^" + escapedType + "$", "i")
                            );
                        })
                        .toList();
                query.addCriteria(new Criteria().orOperator(typeCriteriaList.toArray(new Criteria[0])));
            }
        }

        // Filter by author (creator name)
        if (author != null && !author.isBlank()) {
            String escapedAuthor = java.util.regex.Pattern.quote(author);
            query.addCriteria(Criteria.where("creatorName").regex(escapedAuthor, "i"));
        }

        // Filter by labels (tags)
        if (labels != null && !labels.isEmpty()) {
            query.addCriteria(Criteria.where("tags").all(labels));
        }

        Criteria assigneeCriteria = buildAssigneeCriteria(assignees);
        if (assigneeCriteria != null) {
            query.addCriteria(assigneeCriteria);
        }

        long totalTickets = template.count(query, Ticket.class, CollectionName.TICKETS);

        int skip = (page - 1) * limit;

        // Apply sorting
        Sort sorting = switch (sort != null ? sort : "newest") {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "created");
            case "recently-updated" -> Sort.by(Sort.Direction.DESC, "updatedAt");
            case "least-recently-updated" -> Sort.by(Sort.Direction.ASC, "updatedAt");
            default -> Sort.by(Sort.Direction.DESC, "created"); // "newest"
        };
        query.with(sorting);
        query.skip(skip).limit(limit);

        List<Ticket> tickets = template.find(query, Ticket.class, CollectionName.TICKETS);

        List<TicketListItemResponse> ticketItems = tickets.stream()
                .map(this::toListItemResponse)
                .toList();

        int totalPages = (int) Math.ceil((double) totalTickets / limit);

        return new PaginatedTicketsResponse(
                ticketItems,
                new PaginatedTicketsResponse.PaginationInfo(
                        page,
                        totalPages,
                        limit,
                        totalTickets,
                        page < totalPages,
                        page > 1
                ),
                new PaginatedTicketsResponse.FiltersInfo(search, status, types)
        );
    }

    public Map<String, Long> getTicketCounts(Server server, String search, List<String> types, String author, List<String> labels, List<String> assignees) {
        MongoTemplate template = getTemplate(server);

        // Build base query without status filter
        Query baseQuery = new Query();
        baseQuery.addCriteria(Criteria.where("status").ne("Unfinished"));

        if (search != null && !search.isBlank()) {
            String escapedSearch = java.util.regex.Pattern.quote(search);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("_id").regex(escapedSearch, "i"),
                    Criteria.where("subject").regex(escapedSearch, "i"),
                    Criteria.where("creatorName").regex(escapedSearch, "i")
            );
            baseQuery.addCriteria(searchCriteria);
        }

        // Filter by types (OR logic for multiple types)
        if (types != null && !types.isEmpty()) {
            List<String> validTypes = types.stream()
                    .filter(t -> t != null && !t.isBlank() && !t.equals("all"))
                    .toList();
            if (!validTypes.isEmpty()) {
                List<Criteria> typeCriteriaList = validTypes.stream()
                        .flatMap(type -> {
                            String escapedType = java.util.regex.Pattern.quote(type);
                            return java.util.stream.Stream.of(
                                Criteria.where("type").regex("^" + escapedType + "$", "i"),
                                Criteria.where("category").regex("^" + escapedType + "$", "i")
                            );
                        })
                        .toList();
                baseQuery.addCriteria(new Criteria().orOperator(typeCriteriaList.toArray(new Criteria[0])));
            }
        }

        if (author != null && !author.isBlank()) {
            String escapedAuthor = java.util.regex.Pattern.quote(author);
            baseQuery.addCriteria(Criteria.where("creatorName").regex(escapedAuthor, "i"));
        }

        if (labels != null && !labels.isEmpty()) {
            baseQuery.addCriteria(Criteria.where("tags").all(labels));
        }

        Criteria assigneeCriteria = buildAssigneeCriteria(assignees);
        if (assigneeCriteria != null) {
            baseQuery.addCriteria(assigneeCriteria);
        }

        // Count open tickets
        Query openQuery = Query.of(baseQuery);
        openQuery.addCriteria(Criteria.where("locked").ne(true));
        long openCount = template.count(openQuery, Ticket.class, CollectionName.TICKETS);

        // Count closed tickets
        Query closedQuery = Query.of(baseQuery);
        closedQuery.addCriteria(Criteria.where("locked").is(true));
        long closedCount = template.count(closedQuery, Ticket.class, CollectionName.TICKETS);

        Map<String, Long> counts = new HashMap<>();
        counts.put("open", openCount);
        counts.put("closed", closedCount);
        return counts;
    }

    public int bulkUpdateTickets(Server server, BulkTicketUpdateRequest request, String staffEmail) {
        MongoTemplate template = getTemplate(server);
        int updatedCount = 0;

        for (String ticketId : request.ticketIds()) {
            Query query = Query.query(Criteria.where("_id").is(ticketId));
            Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

            if (ticket == null) {
                continue;
            }

            Update update = new Update().set("updatedAt", new Date());
            boolean hasChanges = false;

            // Update locked status
            if (request.locked() != null) {
                update.set("locked", request.locked());
                if (request.locked()) {
                    update.set("status", "Closed");
                } else {
                    update.set("status", "Open");
                }
                hasChanges = true;
            }

            // Update hidden status
            if (request.hidden() != null) {
                update.set("hidden", request.hidden());
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
                update.set("tags", currentTags);
                hasChanges = true;
            }

            // Remove labels (tags)
            if (request.removeLabels() != null && !request.removeLabels().isEmpty()) {
                List<String> currentTags = ticket.getTags() != null ? new ArrayList<>(ticket.getTags()) : new ArrayList<>();
                currentTags.removeAll(request.removeLabels());
                update.set("tags", currentTags);
                hasChanges = true;
            }

            // Update assignee
            if (request.assignTo() != null) {
                List<String> assignees = "none".equalsIgnoreCase(request.assignTo())
                        ? List.of()
                        : TicketAssigneeUtil.normalizeCsv(request.assignTo());
                update.set("assignedTo", assignees);
                hasChanges = true;
            }

            if (hasChanges) {
                template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);
                updatedCount++;

                // Send transcript email when ticket is closed via bulk operation
                if (Boolean.TRUE.equals(request.locked())) {
                    notificationService.notifyTicketClosed(server, ticket);
                }
            }
        }

        return updatedCount;
    }

    public Optional<TicketResponse> getTicketById(Server server, String ticketId) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(ticketId));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);
        return Optional.ofNullable(ticket).map(this::toTicketResponse);
    }

    public Optional<Ticket> getTicketRaw(Server server, String ticketId) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(ticketId));
        return Optional.ofNullable(template.findOne(query, Ticket.class, CollectionName.TICKETS));
    }

    public TicketResponse createTicket(Server server, CreateTicketRequest request) {
        MongoTemplate template = getTemplate(server);

        TicketType ticketType = TicketType.fromId(request.type());
        String ticketId = generateTicketId(template, ticketType);

        boolean isReport = "player".equalsIgnoreCase(request.type()) || "chat".equalsIgnoreCase(request.type());
        String ticketStatus = isReport || (request.subject() != null && !request.subject().isBlank()) ? "Open" : "Unfinished";
        String subject = (request.subject() != null && !request.subject().isBlank())
                ? request.subject()
                : ticketType.getDisplayName();

        List<String> tags = request.tags() != null ? new ArrayList<>(request.tags()) : new ArrayList<>();
        if (tags.isEmpty()) {
            tags.add(request.type());
        }

        Map<String, Object> data = new HashMap<>();
        if (request.priority() != null) {
            data.put("priority", request.priority());
        }
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
                .type(request.type())
                .category(request.type())
                .subject(subject)
                .status(ticketStatus)
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
                .locked(false)
                .priority(request.priority())
                .emailAuthEnabled(emailAuth)
                .created(new Date())
                .updatedAt(new Date())
                .build();

        template.save(ticket, CollectionName.TICKETS);

        return toTicketResponse(ticket);
    }

    public Optional<TicketResponse> updateTicket(Server server, String ticketId, UpdateTicketRequest request, String staffEmail) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(ticketId));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (ticket == null) {
            return Optional.empty();
        }

        Update update = new Update().set("updatedAt", new Date());

        if (request.status() != null) {
            // Validate status is one of the allowed values
            String validatedStatus = switch (request.status().toLowerCase()) {
                case "open" -> "Open";
                case "closed" -> "Closed";
                case "unfinished" -> "Unfinished";
                default -> "Open";
            };
            update.set("status", validatedStatus);
            ticket.setStatus(validatedStatus);
        }

        if (request.locked() != null) {
            update.set("locked", request.locked());
            ticket.setLocked(request.locked());
        }

        if (request.tags() != null) {
            update.set("tags", request.tags());
            ticket.setTags(request.tags());
        }

        if (request.hidden() != null) {
            update.set("hidden", request.hidden());
            ticket.setHidden(request.hidden());
        }

        if (request.data() != null && !request.data().isEmpty()) {
            Map<String, Object> existingData = ticket.getData() != null ? new HashMap<>(ticket.getData()) : new HashMap<>();
            existingData.putAll(request.data());
            update.set("data", existingData);
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

            update.push("replies", newReply);
            ticket.getReplies().add(newReply);
        }

        if (request.newNote() != null) {
            TicketNote newNote = TicketNote.builder()
                    .text(request.newNote().text())
                    .issuerName(request.newNote().issuerName())
                    .issuerAvatar(request.newNote().issuerAvatar())
                    .date(new Date())
                    .build();

            update.push("notes", newNote);
            ticket.getNotes().add(newNote);
        }

        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        // Send notifications for staff replies
        if (newReply != null && newReply.isStaff()) {
            notificationService.notifyTicketReply(server, ticket, newReply);
        }

        // Send transcript email when ticket is closed (locked)
        if (Boolean.TRUE.equals(request.locked())) {
            notificationService.notifyTicketClosed(server, ticket);
        }

        return Optional.of(toTicketResponse(ticket));
    }

    public Optional<TicketReply> addReply(Server server, String ticketId, AddReplyRequest request) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(ticketId));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

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

        Update update = new Update()
                .push("replies", newReply)
                .set("updatedAt", new Date());

        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        // Send notifications for staff replies
        if (request.staff()) {
            notificationService.notifyTicketReply(server, ticket, newReply);
        }

        return Optional.of(newReply);
    }

    public Optional<TicketNote> addNote(Server server, String ticketId, AddNoteRequest request) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(ticketId));
        if (!template.exists(query, Ticket.class, CollectionName.TICKETS)) {
            return Optional.empty();
        }

        TicketNote newNote = TicketNote.builder()
                .text(request.text())
                .issuerName(request.issuerName())
                .issuerAvatar(request.issuerAvatar())
                .date(new Date())
                .build();

        Update update = new Update()
                .push("notes", newNote)
                .set("updatedAt", new Date());

        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        return Optional.of(newNote);
    }

    public Optional<List<String>> addTag(Server server, String ticketId, String tag) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(ticketId));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (ticket == null) {
            return Optional.empty();
        }

        List<String> tags = ticket.getTags() != null ? new ArrayList<>(ticket.getTags()) : new ArrayList<>();
        if (!tags.contains(tag)) {
            tags.add(tag);
            Update update = new Update()
                    .set("tags", tags)
                    .set("updatedAt", new Date());
            template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);
        }

        return Optional.of(tags);
    }

    public Optional<List<String>> removeTag(Server server, String ticketId, String tag) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(ticketId));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (ticket == null) {
            return Optional.empty();
        }

        List<String> tags = ticket.getTags() != null ? new ArrayList<>(ticket.getTags()) : new ArrayList<>();
        if (tags.remove(tag)) {
            Update update = new Update()
                    .set("tags", tags)
                    .set("updatedAt", new Date());
            template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);
        }

        return Optional.of(tags);
    }

    public List<Ticket> getTicketsByPlayer(Server server, String playerUuid) {
        MongoTemplate template = getTemplate(server);

        Criteria criteria = new Criteria().andOperator(
                new Criteria().orOperator(
                        Criteria.where("creatorUuid").is(playerUuid),
                        Criteria.where("reportedPlayerUuid").is(playerUuid)
                ),
                Criteria.where("status").ne("Unfinished")
        );

        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "created"));
        return template.find(query, Ticket.class, CollectionName.TICKETS);
    }

    public List<Ticket> getTicketsByTag(Server server, String tag) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("tags").is(tag));
        return template.find(query, Ticket.class, CollectionName.TICKETS);
    }

    public QuickResponseResult processQuickResponse(Server server, String ticketId, QuickResponseRequest request, String staffUsername) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(ticketId));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

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

        Update update = new Update()
                .push("replies", responseReply)
                .set("updatedAt", new Date());

        boolean ticketClosed = false;
        if (Boolean.TRUE.equals(action.getCloseTicket())) {
            update.set("locked", true);
            update.set("status", "Closed");
            ticketClosed = true;
        }

        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        // Send notifications for the quick response (which is a staff reply)
        notificationService.notifyTicketReply(server, ticket, responseReply);

        // Send transcript email if ticket was closed
        if (ticketClosed) {
            notificationService.notifyTicketClosed(server, ticket);
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
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(ticketId));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (ticket == null) {
            return Optional.empty();
        }

        Update update = new Update()
                .set("status", "Open")
                .set("updatedAt", new Date());

        Map<String, Object> requestFormData = request.formData() != null ? request.formData() : Collections.emptyMap();
        FormDataProcessingResult formDataProcessing = processFormDataForContent(requestFormData);
        List<Object> initialAttachments = mergeAttachments(
                normalizeAttachments(request.attachments()),
                formDataProcessing.attachments()
        );

        if (request.subject() != null) {
            update.set("subject", request.subject());
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
                update.set("emailAuthEnabled", emailAuth);
                ticket.setEmailAuthEnabled(emailAuth);
                existingData.put("emailAuthEnabled", emailAuth);
            }

            update.set("formData", request.formData());
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
            update.set("data", existingData);
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
                update.push("replies", initialReply);
                if (ticket.getReplies() == null) {
                    ticket.setReplies(new ArrayList<>());
                }
                ticket.getReplies().add(initialReply);
            }
        }

        ticket.setStatus("Open");
        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        return Optional.of(toTicketResponse(ticket));
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

    private String generateTicketId(MongoTemplate template, TicketType type) {
        String prefix = TicketType.getPrefix(type);
        String ticketId;
        int attempts = 0;

        do {
            int randomId = 100000 + RANDOM.nextInt(900000);
            ticketId = prefix + "-" + randomId;
            attempts++;
        } while (template.exists(Query.query(Criteria.where("_id").is(ticketId)), Ticket.class, CollectionName.TICKETS) && attempts < 10);

        return ticketId;
    }

    private Criteria buildAssigneeCriteria(List<String> assignees) {
        if (assignees == null || assignees.isEmpty()) {
            return null;
        }

        List<Criteria> assigneeCriteriaList = new ArrayList<>();
        for (String assignee : assignees) {
            if (assignee == null || assignee.isBlank()) {
                continue;
            }

            if ("none".equalsIgnoreCase(assignee)) {
                assigneeCriteriaList.add(buildUnassignedCriteria());
                continue;
            }

            String normalizedAssignee = TicketAssigneeUtil.normalizeSingle(assignee);
            if (normalizedAssignee != null) {
                // Equality on array fields matches documents where the array contains the value.
                assigneeCriteriaList.add(Criteria.where("assignedTo").is(normalizedAssignee));
            }
        }

        if (assigneeCriteriaList.isEmpty()) {
            return null;
        }

        return new Criteria().orOperator(assigneeCriteriaList.toArray(new Criteria[0]));
    }

    private Criteria buildUnassignedCriteria() {
        return new Criteria().orOperator(
                Criteria.where("assignedTo").exists(false),
                Criteria.where("assignedTo").is(null),
                Criteria.where("assignedTo").size(0)
        );
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
                ticket.getStatus(),
                creatorName,
                creatorName,
                ticket.getCreated(),
                TicketType.fromId(ticket.getType()).getDisplayName(),
                ticket.isLocked(),
                ticket.getType(),
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
                ticket.getType(),
                TicketType.fromId(ticket.getType()).getDisplayName(),
                ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
                ticket.getStatus(),
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

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
