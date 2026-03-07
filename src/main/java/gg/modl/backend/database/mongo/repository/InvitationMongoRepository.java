package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.staff.data.Invitation;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationMongoRepository extends AbstractServerMongoRepository<Invitation> {
    public InvitationMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(Invitation.class, CollectionName.INVITATIONS, diffService, tenantMongoAccess);
    }
}
