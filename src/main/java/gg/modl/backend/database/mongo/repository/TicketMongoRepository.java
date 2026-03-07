package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.ticket.data.Ticket;
import org.springframework.stereotype.Repository;

@Repository
public class TicketMongoRepository extends AbstractServerMongoRepository<Ticket> {
    public TicketMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(Ticket.class, CollectionName.TICKETS, diffService, tenantMongoAccess);
    }
}
