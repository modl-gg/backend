package gg.modl.backend.ticket.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final QuickResponseSettingsService quickResponseSettingsService;

    private static final SecureRandom RANDOM = new SecureRandom();

    public PaginatedTicketsResponse searchTickets(Server server, int page, int limit, String search, String status, String type) {
        MongoTemplate template = getTemplate(server);

        Query query = new Query();
        query.addCriteria(Criteria.where("status").ne("Unfinished"));

        if (search != null && !search.isBlank()) {
            String escapedSearch = java.util.regex.Pattern.quote(search);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("_id").regex(escapedSearch, "i"),
                    Criteria.where("subject").regex(escapedSearch, "i"),
                    Criteria.where("creator").regex(escapedSearch, "i"),
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

        if (type != null && !type.isBlank() && !type.equals("all")) {
            // Check both type and category fields (category preserves original type for player/chat -> REPORT mappings)
            Criteria typeCriteria = new Criteria().orOperator(
                    Criteria.where("type").regex("^" + type + "$", "i"),
                    Criteria.where("category").regex("^" + type + "$", "i")
            );
            query.addCriteria(typeCriteria);
        }

        long totalTickets = template.count(query, Ticket.class, CollectionName.TICKETS);

        int skip = (page - 1) * limit;
        query.with(Sort.by(Sort.Direction.DESC, "created"));
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
                new PaginatedTicketsResponse.FiltersInfo(search, status, type)
        );
    }

    public Optional<TicketResponse> getTicketById(Server server, String ticketId) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(ticketId));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);
        return Optional.ofNullable(ticket).map(this::toTicketResponse);
    }

    public TicketResponse createTicket(Server server, CreateTicketRequest request) {
        MongoTemplate template = getTemplate(server);

        TicketType ticketType = TicketType.fromId(request.type());
        String ticketId = generateTicketId(template, ticketType);

        String ticketStatus = (request.subject() != null && !request.subject().isBlank()) ? "Open" : "Unfinished";
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
        if (request.creatorEmail() != null) {
            data.put("creatorEmail", request.creatorEmail());
        }
        if (request.creatorIdentifier() != null) {
            data.put("creatorIdentifier", request.creatorIdentifier());
        }

        List<TicketReply> replies = new ArrayList<>();
        String content = buildTicketContent(request);
        if (!content.isBlank()) {
            TicketReply initialReply = TicketReply.builder()
                    .id(UUID.randomUUID().toString())
                    .name(request.creatorName() != null ? request.creatorName() : "API User")
                    .content(content)
                    .type("user")
                    .created(new Date())
                    .staff(false)
                    .creatorIdentifier(request.creatorIdentifier())
                    .build();
            replies.add(initialReply);
        }

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .type(request.type())
                .category(request.type())
                .subject(subject)
                .status(ticketStatus)
                .creator(request.creatorName() != null ? request.creatorName() : "API User")
                .creatorUuid(request.creatorUuid())
                .reportedPlayer(request.reportedPlayerName())
                .reportedPlayerUuid(request.reportedPlayerUuid())
                .tags(tags)
                .replies(replies)
                .notes(new ArrayList<>())
                .chatMessages(request.chatMessages())
                .formData(request.formData())
                .data(data)
                .locked(false)
                .priority(request.priority())
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
            update.set("status", request.status());
            ticket.setStatus(request.status());
        }

        if (request.locked() != null) {
            update.set("locked", request.locked());
            ticket.setLocked(request.locked());
        }

        if (request.tags() != null) {
            update.set("tags", request.tags());
            ticket.setTags(request.tags());
        }

        if (request.data() != null && !request.data().isEmpty()) {
            Map<String, Object> existingData = ticket.getData() != null ? new HashMap<>(ticket.getData()) : new HashMap<>();
            existingData.putAll(request.data());
            update.set("data", existingData);
            ticket.setData(existingData);
        }

        if (request.newReply() != null) {
            TicketReply newReply = TicketReply.builder()
                    .id(UUID.randomUUID().toString())
                    .name(request.newReply().name())
                    .avatar(request.newReply().avatar())
                    .content(request.newReply().content())
                    .type(request.newReply().type() != null ? request.newReply().type() : "public")
                    .created(new Date())
                    .staff(request.newReply().staff())
                    .action(request.newReply().action())
                    .attachments(request.newReply().attachments())
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
                .attachments(request.attachments())
                .creatorIdentifier(request.creatorIdentifier())
                .build();

        Update update = new Update()
                .push("replies", newReply)
                .set("updatedAt", new Date());

        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

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

        if (request.subject() != null) {
            update.set("subject", request.subject());
            ticket.setSubject(request.subject());
        }

        if (request.formData() != null && !request.formData().isEmpty()) {
            Map<String, Object> existingData = ticket.getData() != null ? new HashMap<>(ticket.getData()) : new HashMap<>();
            existingData.putAll(request.formData());

            if (request.formData().containsKey("contact_email") || request.formData().containsKey("email")) {
                Object email = request.formData().getOrDefault("contact_email", request.formData().get("email"));
                if (email != null) {
                    existingData.put("creatorEmail", email);
                }
            }

            if (request.creatorIdentifier() != null) {
                existingData.put("creatorIdentifier", request.creatorIdentifier());
            }

            update.set("data", existingData);
            update.set("formData", request.formData());
            ticket.setData(existingData);
            ticket.setFormData(request.formData());

            // Create initial reply from form data (only if no replies exist yet)
            if (ticket.getReplies() == null || ticket.getReplies().isEmpty()) {
                String content = buildFormDataContent(request.formData());
                if (!content.isBlank()) {
                    TicketReply initialReply = TicketReply.builder()
                            .id(UUID.randomUUID().toString())
                            .name(ticket.getCreatorName() != null ? ticket.getCreatorName() : "Player")
                            .content(content)
                            .type("user")
                            .created(new Date())
                            .staff(false)
                            .creatorIdentifier(request.creatorIdentifier())
                            .build();
                    update.push("replies", initialReply);
                    ticket.getReplies().add(initialReply);
                }
            }
        }

        ticket.setStatus("Open");
        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        return Optional.of(toTicketResponse(ticket));
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

    private String buildTicketContent(CreateTicketRequest request) {
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

        if (request.formData() != null && !request.formData().isEmpty()) {
            for (Map.Entry<String, Object> entry : request.formData().entrySet()) {
                if (entry.getValue() != null && !entry.getValue().toString().isBlank()) {
                    String formattedKey = formatFormDataKey(entry.getKey());
                    content.append("**").append(formattedKey).append(":** ").append(entry.getValue()).append("\n\n");
                }
            }
        }

        return content.toString().trim();
    }

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

        if (ticket.getReplies() != null && !ticket.getReplies().isEmpty()) {
            replyCount = ticket.getReplies().size();
            lastReply = ticket.getReplies().get(replyCount - 1);
        }

        return new TicketListItemResponse(
                ticket.getId(),
                ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
                ticket.getStatus(),
                ticket.getCreator(),
                ticket.getCreatorName() != null ? ticket.getCreatorName() : ticket.getCreator(),
                ticket.getCreated(),
                TicketType.fromId(ticket.getType()).getDisplayName(),
                ticket.isLocked(),
                ticket.getType(),
                lastReply,
                replyCount
        );
    }

    private TicketResponse toTicketResponse(Ticket ticket) {
        // Ensure all replies have proper names
        List<TicketReply> processedReplies = processRepliesWithNames(ticket);

        return new TicketResponse(
                ticket.getId(),
                ticket.getType(),
                TicketType.fromId(ticket.getType()).getDisplayName(),
                ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
                ticket.getStatus(),
                ticket.getCreatorName() != null ? ticket.getCreatorName() : ticket.getCreator(),
                ticket.getCreatorUuid(),
                ticket.getCreatorName() != null ? ticket.getCreatorName() : ticket.getCreator(),
                ticket.getReportedPlayer(),
                ticket.getReportedPlayerUuid(),
                ticket.getCreated(),
                ticket.isLocked(),
                processedReplies,
                ticket.getNotes(),
                ticket.getTags(),
                ticket.getFormData(),
                ticket.getData(),
                ticket.getChatMessages()
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

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
