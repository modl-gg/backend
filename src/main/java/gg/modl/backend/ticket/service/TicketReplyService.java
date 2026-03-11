package gg.modl.backend.ticket.service;

import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketNote;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.request.AddNoteRequest;
import gg.modl.backend.ticket.dto.request.AddReplyRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketReplyService {
    private final TicketMongoRepository ticketRepository;
    private final TicketNotificationService notificationService;

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
}
