package gg.modl.backend.ticket.service;

import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketNote;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.request.AddNoteRequest;
import gg.modl.backend.ticket.dto.request.AddReplyRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketReplyService {
    private final TicketMongoRepository ticketRepository;
    private final TicketNotificationService notificationService;

    public TicketReply addReply(Server server, String ticketId, AddReplyRequest request) {
        Ticket ticket = ticketRepository.findById(server, ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

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
        ticket.ensureReplies().add(newReply);
        ticket.setUpdatedAt(new Date());
        Ticket saved = ticketRepository.saveEntity(server, ticket);

        if (request.staff()) {
            notificationService.notifyTicketReply(server, saved, newReply);
        }

        return newReply;
    }

    public TicketNote addNote(Server server, String ticketId, AddNoteRequest request) {
        Ticket ticket = ticketRepository.findById(server, ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        TicketNote newNote = TicketNote.builder()
            .text(request.text())
            .issuerName(request.issuerName())
            .issuerAvatar(request.issuerAvatar())
            .date(new Date())
            .build();
        ticket.ensureNotes().add(newNote);
        ticket.setUpdatedAt(new Date());
        ticketRepository.saveEntity(server, ticket);

        return newNote;
    }

    public List<String> addTag(Server server, String ticketId, String tag) {
        Ticket ticket = ticketRepository.findById(server, ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        List<String> tags = ticket.getTags() != null ? new ArrayList<>(ticket.getTags()) : new ArrayList<>();
        if (!tags.contains(tag)) {
            tags.add(tag);
            ticket.setTags(tags);
            ticket.setUpdatedAt(new Date());
            ticketRepository.saveEntity(server, ticket);
        }

        return tags;
    }

    public List<String> removeTag(Server server, String ticketId, String tag) {
        Ticket ticket = ticketRepository.findById(server, ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        List<String> tags = ticket.getTags() != null ? new ArrayList<>(ticket.getTags()) : new ArrayList<>();
        if (tags.remove(tag)) {
            ticket.setTags(tags);
            ticket.setUpdatedAt(new Date());
            ticketRepository.saveEntity(server, ticket);
        }

        return tags;
    }

}
