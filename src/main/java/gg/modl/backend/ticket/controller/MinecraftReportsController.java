package gg.modl.backend.ticket.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_REPORTS)
@RequiredArgsConstructor
public class MinecraftReportsController {
    private final DynamicMongoTemplateProvider mongoProvider;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllReports(
            @RequestParam(defaultValue = "open") String status,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        // Reports are stored with type = "REPORT" and category = "player"/"chat"/etc.
        // Query for REPORT type tickets (player reports and chat reports)
        Criteria criteria = Criteria.where("type").is("REPORT");

        if (!"all".equalsIgnoreCase(status)) {
            // Handle case-insensitive status matching (Open vs open)
            String capitalizedStatus = status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase();
            criteria = criteria.and("status").in(status, capitalizedStatus);
        }

        Query query = Query.query(criteria);
        query.limit(Math.min(limit, 100));

        List<Ticket> tickets = template.find(query, Ticket.class, CollectionName.TICKETS);

        List<Map<String, Object>> reports = tickets.stream().map(t -> {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("id", t.getId());
            report.put("type", t.getCategory() != null ? t.getCategory() : t.getType()); // Use category (player/chat) as the type
            report.put("category", t.getCategory()); // Original report category
            report.put("reporterName", t.getCreatorName() != null ? t.getCreatorName() : t.getCreator());
            report.put("reporterUuid", t.getCreatorUuid());
            report.put("reportedPlayerUuid", t.getReportedPlayerUuid());
            report.put("reportedPlayerName", t.getReportedPlayer());
            report.put("subject", t.getSubject());
            report.put("content", t.getReplies() != null && !t.getReplies().isEmpty()
                    ? t.getReplies().get(0).getContent() : null); // First reply is the report content
            report.put("status", t.getStatus());
            report.put("priority", t.getPriority());
            report.put("createdAt", t.getCreated());
            report.put("assignedTo", t.getAssignedTo());
            report.put("chatMessages", t.getChatMessages()); // Include chat messages for chat reports
            return report;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "reports", reports
        ));
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Map<String, Object>> dismissReport(
            @PathVariable String id,
            @RequestBody @Valid DismissReportRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("id").is(id));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (ticket == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Report not found"
            ));
        }

        Update update = new Update()
                .set("status", "dismissed")
                .set("updatedAt", new Date());

        if (request.reason() != null && !request.reason().isBlank()) {
            update.set("data.dismissReason", request.reason());
        }
        if (request.dismissedBy() != null) {
            update.set("data.dismissedBy", request.dismissedBy());
            update.set("data.dismissedAt", new Date());
        }

        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Report dismissed"
        ));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveReport(
            @PathVariable String id,
            @RequestBody @Valid ResolveReportRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("id").is(id));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (ticket == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Report not found"
            ));
        }

        Update update = new Update()
                .set("status", "resolved")
                .set("updatedAt", new Date());

        if (request.resolution() != null && !request.resolution().isBlank()) {
            update.set("data.resolution", request.resolution());
        }
        if (request.resolvedBy() != null) {
            update.set("data.resolvedBy", request.resolvedBy());
            update.set("data.resolvedAt", new Date());
        }
        if (request.punishmentId() != null) {
            update.set("data.linkedPunishmentId", request.punishmentId());
        }

        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Report resolved"
        ));
    }

    /**
     * Get reports filed against a specific player
     */
    @GetMapping("/player/{uuid}")
    public ResponseEntity<Map<String, Object>> getPlayerReports(
            @PathVariable String uuid,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        // Query for reports against this player
        Criteria criteria = Criteria.where("type").is("REPORT")
                .and("reportedPlayerUuid").is(uuid);

        if (!"all".equalsIgnoreCase(status)) {
            String capitalizedStatus = status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase();
            criteria = criteria.and("status").in(status, capitalizedStatus);
        }

        Query query = Query.query(criteria);
        query.limit(Math.min(limit, 100));

        List<Ticket> tickets = template.find(query, Ticket.class, CollectionName.TICKETS);

        List<Map<String, Object>> reports = tickets.stream().map(t -> {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("id", t.getId());
            report.put("type", t.getCategory() != null ? t.getCategory() : t.getType());
            report.put("category", t.getCategory());
            report.put("reporterName", t.getCreatorName() != null ? t.getCreatorName() : t.getCreator());
            report.put("reporterUuid", t.getCreatorUuid());
            report.put("reportedPlayerUuid", t.getReportedPlayerUuid());
            report.put("reportedPlayerName", t.getReportedPlayer());
            report.put("subject", t.getSubject());
            report.put("content", t.getReplies() != null && !t.getReplies().isEmpty()
                    ? t.getReplies().get(0).getContent() : null);
            report.put("status", t.getStatus());
            report.put("priority", t.getPriority());
            report.put("createdAt", t.getCreated());
            report.put("assignedTo", t.getAssignedTo());
            report.put("chatMessages", t.getChatMessages());
            return report;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "reports", reports
        ));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignReport(
            @PathVariable String id,
            @RequestBody @Valid AssignReportRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("id").is(id));
        Ticket ticket = template.findOne(query, Ticket.class, CollectionName.TICKETS);

        if (ticket == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Report not found"
            ));
        }

        Update update = new Update()
                .set("assignedTo", request.assignee())
                .set("status", "in_progress")
                .set("updatedAt", new Date());

        template.updateFirst(query, update, Ticket.class, CollectionName.TICKETS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Report assigned"
        ));
    }

    public record DismissReportRequest(
            String dismissedBy,
            String reason
    ) {}

    public record ResolveReportRequest(
            String resolvedBy,
            String resolution,
            String punishmentId
    ) {}

    public record AssignReportRequest(
            @NotBlank String assignee
    ) {}
}
