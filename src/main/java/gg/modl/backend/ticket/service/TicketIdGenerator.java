package gg.modl.backend.ticket.service;

import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.infrastructure.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketIdGenerator {

    private static final int MAX_ATTEMPTS = 10;

    private final IdGenerator idGenerator;
    private final TicketMongoRepository ticketRepository;

    public Ticket insertWithUniqueId(Server server, String prefix, Ticket ticket) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            ticket.setId(prefix + "-" + idGenerator.nextSixDigitInt());
            try {
                return ticketRepository.insertTicket(server, ticket);
            } catch (DuplicateKeyException ignored) {
            }
        }
        throw new IllegalStateException("Unable to allocate a unique ticket id after " + MAX_ATTEMPTS + " attempts");
    }
}
