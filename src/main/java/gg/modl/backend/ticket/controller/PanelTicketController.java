package gg.modl.backend.ticket.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.dto.request.*;
import gg.modl.backend.ticket.dto.response.PaginatedTicketsResponse;
import gg.modl.backend.ticket.dto.response.QuickResponseResult;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.service.TicketService;
import gg.modl.backend.ticket.service.TicketSubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_TICKETS)
@RequiredArgsConstructor
public class PanelTicketController {
    private final TicketService ticketService;
    private final TicketSubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<PaginatedTicketsResponse> searchTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        PaginatedTicketsResponse response = ticketService.searchTickets(server, page, limit, search, status, type);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicket(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String staffEmail = RequestUtil.getSessionEmail(request);

        if (staffEmail != null && !staffEmail.isBlank()) {
            subscriptionService.markTicketAsRead(server, id, staffEmail);
        }

        return ticketService.getTicketById(server, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @RequestBody @Valid CreateTicketRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        TicketResponse ticket = ticketService.createTicket(server, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateTicket(
            @PathVariable String id,
            @RequestBody @Valid UpdateTicketRequest updateRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String staffEmail = RequestUtil.getSessionEmail(request);

        try {
            return ticketService.updateTicket(server, id, updateRequest, staffEmail)
                    .map(ticket -> ResponseEntity.ok(Map.of(
                            "id", ticket.id(),
                            "status", ticket.status(),
                            "tags", ticket.tags(),
                            "notes", ticket.notes(),
                            "messages", ticket.messages(),
                            "data", ticket.data() != null ? ticket.data() : Map.of(),
                            "locked", ticket.locked()
                    )))
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<?> addNote(
            @PathVariable String id,
            @RequestBody @Valid AddNoteRequest noteRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return ticketService.addNote(server, id, noteRequest)
                .map(note -> ResponseEntity.status(HttpStatus.CREATED).body(note))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<?> addReply(
            @PathVariable String id,
            @RequestBody @Valid AddReplyRequest replyRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String staffEmail = RequestUtil.getSessionEmail(request);

        try {
            var replyOpt = ticketService.addReply(server, id, replyRequest);

            if (replyOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            if (replyRequest.staff() && staffEmail != null && !staffEmail.isBlank()) {
                subscriptionService.ensureSubscription(server, id, staffEmail);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(replyOpt.get());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/tags")
    public ResponseEntity<?> addTag(
            @PathVariable String id,
            @RequestBody @Valid AddTagRequest tagRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return ticketService.addTag(server, id, tagRequest.tag())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/tags/{tag}")
    public ResponseEntity<?> removeTag(
            @PathVariable String id,
            @PathVariable String tag,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return ticketService.removeTag(server, id, tag)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/player/{uuid}")
    public ResponseEntity<?> getTicketsByPlayer(
            @PathVariable String uuid,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        var tickets = ticketService.getTicketsByPlayer(server, uuid);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/tag/{tag}")
    public ResponseEntity<?> getTicketsByTag(
            @PathVariable String tag,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        var tickets = ticketService.getTicketsByTag(server, tag);
        return ResponseEntity.ok(tickets);
    }

    @PostMapping("/{id}/quick-response")
    public ResponseEntity<?> quickResponse(
            @PathVariable String id,
            @RequestBody @Valid QuickResponseRequest quickRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String staffEmail = RequestUtil.getSessionEmail(request);

        String staffUsername = staffEmail != null ? staffEmail.split("@")[0] : "System";

        QuickResponseResult result = ticketService.processQuickResponse(server, id, quickRequest, staffUsername);

        if (!result.success()) {
            return ResponseEntity.badRequest().body(Map.of("error", result.message()));
        }

        return ResponseEntity.ok(result);
    }
}
