package gg.modl.backend.ticket.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketSearchServiceTest {

    @Mock
    private TicketMongoRepository ticketRepository;

    private TicketSearchService ticketSearchService;

    @BeforeEach
    void setUp() {
        ticketSearchService = new TicketSearchService(ticketRepository);
    }

    @Test
    void getTicketsByPlayerLowercasesUuidBeforeQueryingRepository() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        when(ticketRepository.findByPlayer(any(Server.class), any())).thenReturn(List.of());

        ticketSearchService.getTicketsByPlayer(server, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE");

        verify(ticketRepository).findByPlayer(server, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    }
}
