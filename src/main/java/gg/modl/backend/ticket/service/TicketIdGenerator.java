package gg.modl.backend.ticket.service;

import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.infrastructure.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketIdGenerator {

    private final IdGenerator idGenerator;
    private final TicketMongoRepository ticketRepository;

    public String generate(Server server, TicketCategory category) {
        String prefix = category.getTicketPrefix();
        return generateWithPrefix(server, prefix);
    }

    private String generateWithPrefix(Server server, String prefix) {
        String id;
        int attempts = 0;
        do {
            int randomId = idGenerator.nextSixDigitInt();
            id = prefix + "-" + randomId;
            attempts++;
        } while (ticketRepository.existsByTicketId(server, id) && attempts < 10);
        return id;
    }

    public String generateAppealId(Server server) {
        return generateWithPrefix(server, "APPEAL");
    }
}
