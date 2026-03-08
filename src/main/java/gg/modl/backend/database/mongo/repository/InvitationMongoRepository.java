package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.InvitationFields;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Invitation;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public class InvitationMongoRepository extends AbstractServerMongoRepository<Invitation> {
    public InvitationMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(Invitation.class, CollectionName.INVITATIONS, tenantMongoAccess);
    }

    public List<Invitation> findActiveInvitations(Server server, Date now) {
        return find(server, Query.query(MongoQueries.where(InvitationFields.EXPIRES_AT).gt(now)));
    }

    public boolean deleteById(Server server, String id) {
        return remove(server, Query.query(MongoQueries.where(InvitationFields.ID).is(id))).getDeletedCount() > 0;
    }
}
