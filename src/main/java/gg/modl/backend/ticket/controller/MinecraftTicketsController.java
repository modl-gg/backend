package gg.modl.backend.ticket.controller;

import gg.modl.backend.ai.service.AITicketAnalysisService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.request.MinecraftClaimTicketRequest;
import gg.modl.backend.ticket.dto.request.MinecraftCreateTicketRequest;
import gg.modl.backend.ticket.dto.request.MinecraftTicketsByIdsRequest;
import gg.modl.backend.ticket.service.MinecraftTicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_TICKETS)
@RequiredArgsConstructor
public class MinecraftTicketsController {
    private final MinecraftTicketService minecraftTicketService;
    private final AITicketAnalysisService aiTicketAnalysisService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestBody @Valid MinecraftCreateTicketRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        Ticket ticket = minecraftTicketService.createMinecraftTicket(server, request);

        if (TicketCategory.fromCanonicalId(request.type()) == TicketCategory.CHAT
                && request.chatMessages() != null
                && !request.chatMessages().isEmpty()) {
            aiTicketAnalysisService.analyzeTicketAsync(server, ticket.getId());
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "ticketId", ticket.getId(),
                "message", "Ticket created successfully"
        ));
    }

    @PostMapping("/unfinished")
    public ResponseEntity<Map<String, Object>> createUnfinishedTicket(
            @RequestBody @Valid MinecraftCreateTicketRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        Ticket ticket = minecraftTicketService.createUnfinishedMinecraftTicket(server, request);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "ticketId", ticket.getId(),
                "message", "Ticket draft created - complete the form on the panel"
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        List<Map<String, Object>> ticketList = minecraftTicketService.getMinecraftTickets(server, status, type, limit).stream()
                .map(this::toTicketListItem)
                .toList();

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
        Ticket ticket = minecraftTicketService.getMinecraftTicket(server, id).orElse(null);
        if (ticket == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Ticket not found"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "ticket", toTicketDetail(ticket)
        ));
    }

    @GetMapping("/player/{uuid}")
    public ResponseEntity<Map<String, Object>> getPlayerTickets(
            @PathVariable String uuid,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        List<Map<String, Object>> tickets = minecraftTicketService.getMinecraftTicketsByCreator(server, uuid, 50).stream()
                .map(ticket -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("id", ticket.getId());
                    response.put("type", ticket.getType() != null ? ticket.getType().getId() : null);
                    response.put("category", ticket.getType() != null ? ticket.getType().getId() : null);
                    response.put("subject", ticket.getSubject());
                    response.put("status", ticket.getStatus() != null ? ticket.getStatus().getId() : null);
                    response.put("createdAt", ticket.getCreated());
                    return response;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "tickets", tickets
        ));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<Map<String, Object>> claimTicket(
            @PathVariable String id,
            @RequestBody @Valid MinecraftClaimTicketRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftTicketService.MinecraftTicketClaimResult result = minecraftTicketService.claimMinecraftTicket(server, id, request);

        return switch (result.status()) {
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "success", false,
                    "message", "Ticket not found"
            ));
            case ALREADY_LINKED -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", 409,
                    "success", false,
                    "message", "Ticket is already linked to a Minecraft account"
            ));
            case SUCCESS -> ResponseEntity.ok(Map.of(
                    "status", 200,
                    "success", true,
                    "message", "Ticket successfully linked to your account",
                    "ticketId", id,
                    "subject", result.ticket() != null ? result.ticket().getSubject() : null
            ));
        };
    }

    @PostMapping("/by-ids")
    public ResponseEntity<Map<String, Object>> getTicketsByIds(
            @RequestBody @Valid MinecraftTicketsByIdsRequest request,
            HttpServletRequest httpRequest
    ) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "status", 200,
                    "tickets", List.of()
            ));
        }

        Server server = RequestUtil.getRequestServer(httpRequest);
        List<Map<String, Object>> ticketList = minecraftTicketService.getMinecraftTicketsByIds(server, request.ids()).stream()
                .map(this::toTicketLookupItem)
                .toList();

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "tickets", ticketList
        ));
    }

    private Map<String, Object> toTicketListItem(Ticket ticket) {
        boolean hasStaffResponse = false;
        if (ticket.getReplies() != null) {
            hasStaffResponse = ticket.getReplies().stream().anyMatch(TicketReply::isStaff);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", ticket.getId());
        response.put("type", ticket.getType() != null ? ticket.getType().getId() : null);
        response.put("category", ticket.getType() != null ? ticket.getType().getId() : null);
        response.put("subject", ticket.getSubject());
        response.put("status", ticket.getStatus() != null ? ticket.getStatus().getId() : null);
        response.put("playerName", ticket.getCreatorName());
        response.put("playerUuid", ticket.getCreatorUuid());
        response.put("priority", ticket.getPriority() != null ? ticket.getPriority().getId() : null);
        response.put("assignedTo", ticket.getAssignedTo());
        response.put("createdAt", ticket.getCreated());
        response.put("updatedAt", ticket.getUpdatedAt());
        response.put("hasStaffResponse", hasStaffResponse);
        response.put("replyCount", ticket.getReplies() != null ? ticket.getReplies().size() : 0);
        response.put("locked", ticket.isLocked());
        return response;
    }

    private Map<String, Object> toTicketDetail(Ticket ticket) {
        List<Map<String, Object>> replies = new ArrayList<>();
        if (ticket.getReplies() != null) {
            for (TicketReply reply : ticket.getReplies()) {
                Map<String, Object> replyData = new LinkedHashMap<>();
                replyData.put("id", reply.getId());
                replyData.put("content", reply.getContent());
                replyData.put("authorName", reply.getName());
                replyData.put("authorId", reply.getCreatorIdentifier());
                replyData.put("isStaff", reply.isStaff());
                replyData.put("createdAt", reply.getCreated());
                replies.add(replyData);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", ticket.getId());
        response.put("type", ticket.getType() != null ? ticket.getType().getId() : null);
        response.put("category", ticket.getType() != null ? ticket.getType().getId() : null);
        response.put("subject", ticket.getSubject());
        response.put("status", ticket.getStatus() != null ? ticket.getStatus().getId() : null);
        response.put("playerName", ticket.getCreatorName());
        response.put("playerUuid", ticket.getCreatorUuid());
        response.put("priority", ticket.getPriority() != null ? ticket.getPriority().getId() : null);
        response.put("assignedTo", ticket.getAssignedTo());
        response.put("createdAt", ticket.getCreated());
        response.put("updatedAt", ticket.getUpdatedAt());
        response.put("locked", ticket.isLocked());
        response.put("replies", replies);
        response.put("chatMessages", ticket.getChatMessages());
        return response;
    }

    private Map<String, Object> toTicketLookupItem(Ticket ticket) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", ticket.getId());
        response.put("type", ticket.getType() != null ? ticket.getType().getId() : null);
        response.put("category", ticket.getType() != null ? ticket.getType().getId() : null);
        response.put("subject", ticket.getSubject());
        response.put("status", ticket.getStatus() != null ? ticket.getStatus().getId() : null);
        response.put("playerName", ticket.getCreatorName());
        response.put("playerUuid", ticket.getCreatorUuid());
        response.put("createdAt", ticket.getCreated());
        if (ticket.getReplies() != null && !ticket.getReplies().isEmpty()) {
            response.put("firstReplyContent", ticket.getReplies().get(0).getContent());
        }
        return response;
    }
}
