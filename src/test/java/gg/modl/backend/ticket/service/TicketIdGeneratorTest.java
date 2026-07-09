package gg.modl.backend.ticket.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.infrastructure.util.IdGenerator;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class TicketIdGeneratorTest {

    private final Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

    @Test
    void insertWithUniqueIdAssignsPrefixedSixDigitPublicIdShape() {
        TicketMongoRepository ticketRepository = mock(TicketMongoRepository.class);
        when(ticketRepository.insertTicket(any(Server.class), any(Ticket.class)))
            .thenAnswer(invocation -> invocation.getArgument(1));

        TicketIdGenerator generator = new TicketIdGenerator(new FixedIdGenerator(), ticketRepository);

        Ticket result = generator.insertWithUniqueId(server, TicketCategory.SUPPORT.getTicketPrefix(), Ticket.builder().build());

        assertTrue(result.getId().matches("SUPPORT-\\d{6}"));
    }

    @Test
    void insertWithUniqueIdRetriesOnDuplicateKey() {
        TicketMongoRepository ticketRepository = mock(TicketMongoRepository.class);
        when(ticketRepository.insertTicket(any(Server.class), any(Ticket.class)))
            .thenThrow(new DuplicateKeyException("duplicate"))
            .thenAnswer(invocation -> invocation.getArgument(1));

        TicketIdGenerator generator = new TicketIdGenerator(new FixedIdGenerator(), ticketRepository);

        Ticket result = generator.insertWithUniqueId(server, TicketCategory.SUPPORT.getTicketPrefix(), Ticket.builder().build());

        assertTrue(result.getId().matches("SUPPORT-\\d{6}"));
    }

    @Test
    void insertWithUniqueIdThrowsAfterRepeatedCollisions() {
        TicketMongoRepository ticketRepository = mock(TicketMongoRepository.class);
        when(ticketRepository.insertTicket(any(Server.class), any(Ticket.class)))
            .thenThrow(new DuplicateKeyException("duplicate"));

        TicketIdGenerator generator = new TicketIdGenerator(new FixedIdGenerator(), ticketRepository);

        assertThrows(IllegalStateException.class,
            () -> generator.insertWithUniqueId(server, TicketCategory.SUPPORT.getTicketPrefix(), Ticket.builder().build()));
    }

    private static class FixedIdGenerator extends IdGenerator {
        @Override
        public int nextSixDigitInt() {
            return 100000;
        }
    }
}
