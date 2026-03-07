package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.player.data.Player;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerMongoRepository extends AbstractServerMongoRepository<Player> {
    public PlayerMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(Player.class, CollectionName.PLAYERS, diffService, tenantMongoAccess);
    }
}
