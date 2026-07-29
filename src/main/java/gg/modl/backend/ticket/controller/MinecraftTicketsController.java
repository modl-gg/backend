package gg.modl.backend.ticket.controller;

import gg.modl.backend.ai.service.AITicketAnalysisService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.dto.request.MinecraftClaimTicketRequest;
import gg.modl.backend.ticket.dto.request.MinecraftCreateTicketRequest;
import gg.modl.backend.ticket.dto.request.MinecraftTicketsByIdsRequest;
import gg.modl.backend.ticket.dto.response.MinecraftPlayerTicketView;
import gg.modl.backend.ticket.dto.response.MinecraftTicketListItemView;
import gg.modl.backend.ticket.dto.response.MinecraftTicketLookupView;
import gg.modl.backend.ticket.dto.response.MinecraftV1Response;
import gg.modl.backend.ticket.service.MinecraftTicketService;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Slf4j
@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_TICKETS)
@RequiredArgsConstructor
public class MinecraftTicketsController {
    private final MinecraftTicketService minecraftTicketService;
    private final AITicketAnalysisService aiTicketAnalysisService;

    @PostMapping
    public ResponseEntity<MinecraftV1Response> createTicket(
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

        return ResponseEntity.ok(new MinecraftV1Response.TicketCreated(
            200, true, ticket.getId(), "Ticket created successfully"));
    }

    @PostMapping("/unfinished")
    public ResponseEntity<MinecraftV1Response> createUnfinishedTicket(
        @RequestBody @Valid MinecraftCreateTicketRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        Ticket ticket = minecraftTicketService.createUnfinishedMinecraftTicket(server, request);

        return ResponseEntity.ok(new MinecraftV1Response.TicketCreated(
            200, true, ticket.getId(), "Ticket draft created - complete the form on the panel"));
    }

    @GetMapping
    public ResponseEntity<MinecraftV1Response> getAllTickets(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String type,
        @RequestParam(defaultValue = "50") @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) int limit,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        List<MinecraftTicketListItemView> ticketList = minecraftTicketService.getMinecraftTickets(server, status, type, limit)
            .stream()
            .map(minecraftTicketService::toTicketListItem)
            .toList();

        return ResponseEntity.ok(new MinecraftV1Response.TicketList(200, ticketList));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MinecraftV1Response> getTicket(
        @PathVariable String id,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        Ticket ticket = minecraftTicketService.getMinecraftTicket(server, id).orElse(null);
        if (ticket == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new MinecraftV1Response.NotFound(404, "Ticket not found"));
        }

        return ResponseEntity.ok(new MinecraftV1Response.TicketDetail(200, minecraftTicketService.toTicketDetail(ticket)));
    }

    @GetMapping("/player/{uuid}")
    public ResponseEntity<MinecraftV1Response> getPlayerTickets(
        @PathVariable String uuid,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        List<MinecraftPlayerTicketView> tickets = minecraftTicketService.getMinecraftTicketsByCreator(server, uuid, 50)
            .stream()
            .map(minecraftTicketService::toPlayerTicketItem)
            .toList();

        return ResponseEntity.ok(new MinecraftV1Response.PlayerTicketList(200, tickets));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<MinecraftV1Response> claimTicket(
        @PathVariable String id,
        @RequestBody @Valid MinecraftClaimTicketRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftTicketService.MinecraftTicketClaimResult result = minecraftTicketService.claimMinecraftTicket(server, id, request);

        return switch (result.status()) {
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new MinecraftV1Response.TicketClaim(404, false, "Ticket not found", null, null));
            case ALREADY_LINKED -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                new MinecraftV1Response.TicketClaim(409, false, "Ticket is already linked to a Minecraft account", null, null));
            case SUCCESS -> ResponseEntity.ok(new MinecraftV1Response.TicketClaim(
                200, true, "Ticket successfully linked to your account", id, result.ticket().getSubject()));
        };
    }

    @PostMapping("/by-ids")
    public ResponseEntity<MinecraftV1Response> getTicketsByIds(
        @RequestBody @Valid MinecraftTicketsByIdsRequest request,
        HttpServletRequest httpRequest
    ) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return ResponseEntity.ok(new MinecraftV1Response.TicketLookupList(200, List.of()));
        }

        Server server = RequestUtil.getRequestServer(httpRequest);
        List<MinecraftTicketLookupView> ticketList = minecraftTicketService.getMinecraftTicketsByIds(server, request.ids())
            .stream()
            .map(minecraftTicketService::toTicketLookupItem)
            .toList();

        return ResponseEntity.ok(new MinecraftV1Response.TicketLookupList(200, ticketList));
    }

}
