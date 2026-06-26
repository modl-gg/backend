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

    /**
     * Assigns a fresh {@code PREFIX-NNNNNN} id to the already-built ticket and atomically inserts it,
     * regenerating the id and retrying on a duplicate-key collision. This closes the previous
     * check-then-save TOCTOU window (two concurrent creates could both pass an existence check and the
     * second would silently overwrite the first via an _id upsert) by relying on MongoDB's intrinsic
     * _id uniqueness: a real insert of a duplicate id throws {@link DuplicateKeyException}.
     */
    public Ticket insertWithUniqueId(Server server, String prefix, Ticket ticket) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            ticket.setId(prefix + "-" + idGenerator.nextSixDigitInt());
            try {
                return ticketRepository.insertTicket(server, ticket);
            } catch (DuplicateKeyException duplicate) {
                // id already taken (collision or concurrent insert) — regenerate and retry
            }
        }
        throw new IllegalStateException("Unable to allocate a unique ticket id after " + MAX_ATTEMPTS + " attempts");
    }
}
