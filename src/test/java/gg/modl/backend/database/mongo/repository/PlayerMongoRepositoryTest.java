package gg.modl.backend.database.mongo.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.server.data.Server;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class PlayerMongoRepositoryTest {

    @Mock
    private TenantMongoAccess tenantMongoAccess;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private Server server;

    private PlayerMongoRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PlayerMongoRepository(tenantMongoAccess);
        when(tenantMongoAccess.forServer(server)).thenReturn(mongoTemplate);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Player.class), eq(CollectionName.PLAYERS)))
            .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
    }

    @Test
    void updateLoginState_uses_uuid_shaped_player_document_id_in_query() {
        String playerId = UUID.randomUUID().toString();
        Player player = Player.builder()
            .id(playerId)
            .data(new HashMap<>())
            .build();

        repository.updateLoginState(server, player);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateFirst(
            queryCaptor.capture(),
            any(Update.class),
            eq(Player.class),
            eq(CollectionName.PLAYERS)
        );

        assertEquals(playerId, queryCaptor.getValue().getQueryObject().getString("_id"));
    }
}
