package gg.modl.backend.ticket.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.dto.request.AddReplyRequest;
import gg.modl.backend.ticket.dto.request.CreateTicketRequest;
import gg.modl.backend.ticket.dto.request.SubmitTicketFormRequest;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_TICKETS)
@RequiredArgsConstructor
public class PublicTicketController {
    private final TicketService ticketService;

    @PostMapping
    public ResponseEntity<?> createTicket(
            @RequestBody @Valid CreateTicketRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            TicketResponse ticket = ticketService.createTicket(server, createRequest);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "ticketId", ticket.id(),
                    "message", "Ticket created successfully",
                    "ticket", Map.of(
                            "id", ticket.id(),
                            "type", ticket.type(),
                            "subject", ticket.subject(),
                            "status", ticket.status(),
                            "created", ticket.date().toInstant().toString()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error", "message", "Failed to create ticket"));
        }
    }

    @PostMapping("/unfinished")
    public ResponseEntity<?> createUnfinishedTicket(
            @RequestBody @Valid CreateTicketRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            TicketResponse ticket = ticketService.createTicket(server, createRequest);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "ticketId", ticket.id(),
                    "message", "Ticket created successfully (Unfinished)",
                    "ticket", Map.of(
                            "id", ticket.id(),
                            "type", ticket.type(),
                            "subject", ticket.subject(),
                            "status", ticket.status(),
                            "created", ticket.date().toInstant().toString()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error", "message", "Failed to create unfinished ticket"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTicket(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return ticketService.getTicketById(server, id)
                .map(ticket -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("id", ticket.id());
                    response.put("_id", ticket.id());
                    response.put("type", ticket.type());
                    response.put("subject", ticket.subject());
                    response.put("status", ticket.status());
                    response.put("creator", ticket.creator() != null ? ticket.creator() : "");
                    response.put("creatorUuid", ticket.creatorUuid() != null ? ticket.creatorUuid() : "");
                    response.put("reportedBy", ticket.reportedBy() != null ? ticket.reportedBy() : "");
                    response.put("created", ticket.date());
                    response.put("date", ticket.date());
                    response.put("category", ticket.category());
                    response.put("locked", ticket.locked());
                    response.put("replies", ticket.messages() != null ? ticket.messages() : Collections.emptyList());
                    response.put("messages", ticket.messages() != null ? ticket.messages() : Collections.emptyList());
                    response.put("notes", ticket.notes() != null ? ticket.notes() : Collections.emptyList());
                    response.put("tags", ticket.tags() != null ? ticket.tags() : Collections.emptyList());
                    response.put("data", ticket.data() != null ? ticket.data() : Map.of());
                    response.put("formData", ticket.formData() != null ? ticket.formData() : Map.of());
                    response.put("reportedPlayer", ticket.reportedPlayer() != null ? ticket.reportedPlayer() : "");
                    response.put("reportedPlayerUuid", ticket.reportedPlayerUuid() != null ? ticket.reportedPlayerUuid() : "");
                    response.put("chatMessages", ticket.chatMessages() != null ? ticket.chatMessages() : Collections.emptyList());
                    return ResponseEntity.ok(response);
                })
                .<ResponseEntity<?>>map(r -> r)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getTicketStatus(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return ticketService.getTicketById(server, id)
                .map(ticket -> ResponseEntity.ok(Map.of(
                        "id", ticket.id(),
                        "type", ticket.type(),
                        "subject", ticket.subject(),
                        "status", ticket.status(),
                        "created", ticket.date(),
                        "locked", ticket.locked()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<?> addReply(
            @PathVariable String id,
            @RequestBody @Valid AddReplyRequest replyRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            var replyOpt = ticketService.addReply(server, id, replyRequest);

            if (replyOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Reply added successfully",
                    "reply", replyOpt.get()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Forbidden", "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitTicketForm(
            @PathVariable String id,
            @RequestBody SubmitTicketFormRequest submitRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return ticketService.submitTicketForm(server, id, submitRequest)
                .map(ticket -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Ticket submitted successfully",
                        "ticket", Map.of(
                                "id", ticket.id(),
                                "subject", ticket.subject(),
                                "status", ticket.status()
                        )
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
