package gg.modl.backend.ticket.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_TICKETS)
@RequiredArgsConstructor
public class MinecraftTicketsController {
    private final DynamicMongoTemplateProvider mongoProvider;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Create a finished ticket (e.g., player report with all info provided)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestBody @Valid CreateTicketRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        TicketType ticketType = TicketType.fromId(request.type());
        String ticketId = generateTicketId(template, ticketType);
        Date now = new Date();

        // Map chat messages to the expected format
        List<Map<String, Object>> chatMessages = null;
        if (request.chatMessages() != null && !request.chatMessages().isEmpty()) {
            chatMessages = request.chatMessages().stream()
                    .map(msg -> Map.<String, Object>of("content", msg, "timestamp", now))
                    .toList();
        }

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .type(mapTicketType(request.type()))
                .category(request.type())
                .subject(request.subject())
                .status("Open")
                .creator(request.creatorUuid())
                .creatorUuid(request.creatorUuid())
                .creatorName(request.creatorName())
                .reportedPlayer(request.reportedPlayerName())
                .reportedPlayerUuid(request.reportedPlayerUuid())
                .tags(request.tags() != null ? request.tags() : new ArrayList<>())
                .replies(new ArrayList<>())
                .notes(new ArrayList<>())
                .chatMessages(chatMessages)
                .priority(request.priority() != null ? request.priority() : "normal")
                .created(now)
                .updatedAt(now)
                .build();

        // Add initial description as first reply if provided
        if (request.description() != null && !request.description().isBlank()) {
            TicketReply initialReply = TicketReply.builder()
                    .id(new ObjectId().toHexString())
                    .content(request.description())
                    .name(request.creatorName() != null ? request.creatorName() : "Player")
                    .creatorIdentifier(request.creatorUuid())
                    .staff(false)
                    .type("user")
                    .created(now)
                    .build();
            ticket.getReplies().add(initialReply);
        }

        template.save(ticket, CollectionName.TICKETS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "ticketId", ticketId,
                "message", "Ticket created successfully"
        ));
    }

    /**
     * Create an unfinished ticket (e.g., staff application that needs form completion)
     */
    @PostMapping("/unfinished")
    public ResponseEntity<Map<String, Object>> createUnfinishedTicket(
            @RequestBody @Valid CreateTicketRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        TicketType ticketType = TicketType.fromId(request.type());
        String ticketId = generateTicketId(template, ticketType);
        Date now = new Date();

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .type(mapTicketType(request.type()))
                .category(request.type())
                .subject(request.subject())
                .status("Unfinished") // Unfinished tickets start as Unfinished
                .creator(request.creatorUuid())
                .creatorUuid(request.creatorUuid())
                .creatorName(request.creatorName())
                .tags(request.tags() != null ? request.tags() : new ArrayList<>())
                .replies(new ArrayList<>())
                .notes(new ArrayList<>())
                .priority(request.priority() != null ? request.priority() : "normal")
                .created(now)
                .updatedAt(now)
                .build();

        template.save(ticket, CollectionName.TICKETS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "ticketId", ticketId,
                "message", "Ticket draft created - complete the form on the panel"
        ));
    }

    /**
     * Map plugin ticket type to internal type
     */
    private String mapTicketType(String type) {
        if (type == null) return "OTHER";
        return switch (type.toLowerCase()) {
            case "player", "chat" -> "REPORT";
            case "staff" -> "STAFF";
            case "bug" -> "BUG";
            case "support" -> "SUPPORT";
            case "appeal" -> "APPEAL";
            default -> "OTHER";
        };
    }

    /**
     * Generate a readable ticket ID like SUPPORT-123456
     */
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

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Criteria criteria = new Criteria();
        List<Criteria> conditions = new ArrayList<>();

        // Filter by status if provided
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            conditions.add(Criteria.where("status").is(status));
        }

        // Filter by type if provided (support tickets vs reports)
        if (type != null && !type.isBlank()) {
            conditions.add(Criteria.where("type").is(type));
        } else {
            // Default to support tickets (exclude player reports)
            conditions.add(Criteria.where("type").in("SUPPORT", "BUG", "APPEAL", "STAFF", "OTHER"));
        }

        if (!conditions.isEmpty()) {
            criteria = new Criteria().andOperator(conditions.toArray(new Criteria[0]));
        }

        Query query = Query.query(criteria);
        query.with(Sort.by(Sort.Direction.DESC, "created"));
        query.limit(Math.min(limit, 100));

        List<Ticket> tickets = template.find(query, Ticket.class, CollectionName.TICKETS);

        List<Map<String, Object>> ticketList = tickets.stream().map(t -> {
            boolean hasStaffResponse = false;
            if (t.getReplies() != null) {
                hasStaffResponse = t.getReplies().stream()
                        .anyMatch(r -> r.isStaff());
            }

            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("id", t.getId());
            ticket.put("type", t.getType());
            ticket.put("category", t.getCategory());
            ticket.put("subject", t.getSubject());
            ticket.put("status", t.getStatus());
            ticket.put("playerName", t.getCreatorName());
            ticket.put("playerUuid", t.getCreatorUuid());
            ticket.put("priority", t.getPriority());
            ticket.put("assignedTo", t.getAssignedTo());
            ticket.put("createdAt", t.getCreated());
            ticket.put("updatedAt", t.getUpdatedAt());
            ticket.put("hasStaffResponse", hasStaffResponse);
            ticket.put("replyCount", t.getReplies() != null ? t.getReplies().size() : 0);
            ticket.put("locked", t.isLocked());
            return ticket;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "tickets", ticketList
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTicket(
            @PathVariable String id,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("id").is(id));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (ticket == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Ticket not found"
            ));
        }

        List<Map<String, Object>> replies = new ArrayList<>();
        if (ticket.getReplies() != null) {
            for (TicketReply reply : ticket.getReplies()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", reply.getId());
                r.put("content", reply.getContent());
                r.put("authorName", reply.getName());
                r.put("authorId", reply.getCreatorIdentifier());
                r.put("isStaff", reply.isStaff());
                r.put("createdAt", reply.getCreated());
                replies.add(r);
            }
        }

        Map<String, Object> ticketData = new LinkedHashMap<>();
        ticketData.put("id", ticket.getId());
        ticketData.put("type", ticket.getType());
        ticketData.put("category", ticket.getCategory());
        ticketData.put("subject", ticket.getSubject());
        ticketData.put("status", ticket.getStatus());
        ticketData.put("playerName", ticket.getCreatorName());
        ticketData.put("playerUuid", ticket.getCreatorUuid());
        ticketData.put("priority", ticket.getPriority());
        ticketData.put("assignedTo", ticket.getAssignedTo());
        ticketData.put("createdAt", ticket.getCreated());
        ticketData.put("updatedAt", ticket.getUpdatedAt());
        ticketData.put("locked", ticket.isLocked());
        ticketData.put("replies", replies);
        ticketData.put("chatMessages", ticket.getChatMessages());

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "ticket", ticketData
        ));
    }

    @GetMapping("/player/{uuid}")
    public ResponseEntity<Map<String, Object>> getPlayerTickets(
            @PathVariable String uuid,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("creatorUuid").is(uuid));
        query.with(Sort.by(Sort.Direction.DESC, "created"));
        query.limit(50);

        List<Ticket> tickets = template.find(query, Ticket.class, CollectionName.TICKETS);

        List<Map<String, Object>> ticketList = tickets.stream().map(t -> {
            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("id", t.getId());
            ticket.put("type", t.getType());
            ticket.put("subject", t.getSubject());
            ticket.put("status", t.getStatus());
            ticket.put("createdAt", t.getCreated());
            return ticket;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "tickets", ticketList
        ));
    }

    /**
     * Claim an unlinked ticket by linking it to a Minecraft account.
     * This allows players to claim tickets created via web form.
     */
    @PostMapping("/{id}/claim")
    public ResponseEntity<Map<String, Object>> claimTicket(
            @PathVariable String id,
            @RequestBody @Valid ClaimTicketRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("_id").is(id));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (ticket == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "success", false,
                    "message", "Ticket not found"
            ));
        }

        // Check if ticket is already claimed
        if (ticket.getCreatorUuid() != null && !ticket.getCreatorUuid().isBlank()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", 409,
                    "success", false,
                    "message", "Ticket is already linked to a Minecraft account"
            ));
        }

        // Update the ticket with the player's information
        Update update = new Update()
                .set("creatorUuid", request.playerUuid())
                .set("creatorName", request.playerName())
                .set("creator", request.playerUuid())
                .set("updatedAt", new Date());

        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Ticket successfully linked to your account",
                "ticketId", id,
                "subject", ticket.getSubject()
        ));
    }

    /**
     * Request record for claiming a ticket
     */
    public record ClaimTicketRequest(
            @NotBlank String playerUuid,
            @NotBlank String playerName
    ) {}

    /**
     * Request record for creating tickets from the Minecraft plugin
     */
    public record CreateTicketRequest(
            @NotBlank String creatorUuid,
            String creatorName,
            @NotBlank String type,
            String subject,
            String description,
            String reportedPlayerUuid,
            String reportedPlayerName,
            List<String> chatMessages,
            List<String> tags,
            String priority
    ) {}
}
