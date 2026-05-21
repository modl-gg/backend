package gg.modl.backend.database.mongo.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class TicketMongoRepositoryTest {
    @Mock
    private TenantMongoAccess tenantMongoAccess;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private Server server;

    private TicketMongoRepository repository;

    @BeforeEach
    void setUp() {
        repository = new TicketMongoRepository(tenantMongoAccess);
        when(tenantMongoAccess.forServer(server)).thenReturn(mongoTemplate);
        when(mongoTemplate.find(any(Query.class), eq(Ticket.class), eq(CollectionName.TICKETS)))
            .thenReturn(List.of());
    }

    @Test
    void findPlayerTicketsWithReplayUrlFiltersBlankReplayUrlsBeforeApplyingLimit() {
        repository.findPlayerTicketsWithReplayUrl(server, "player-uuid", 100);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Ticket.class), eq(CollectionName.TICKETS));

        String queryText = queryCaptor.getValue().getQueryObject().toJson();
        assertTrue(queryText.contains("replayUrl"), queryText);
        assertTrue(queryText.contains("$exists"), queryText);
        assertTrue(queryText.contains("$nin"), queryText);
    }
}
