package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.InvitationFields;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Invitation;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationMongoRepository extends AbstractServerMongoRepository<Invitation> {
    public InvitationMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(Invitation.class, CollectionName.INVITATIONS, tenantMongoAccess);
    }

    public List<Invitation> findActiveInvitations(Server server, Date now) {
        return find(server, Query.query(Criteria.where(InvitationFields.EXPIRES_AT).gt(now)));
    }

    public long countActive(Server server, Date now) {
        return count(server, Query.query(Criteria.where(InvitationFields.EXPIRES_AT).gt(now)));
    }

    public boolean existsByEmailActive(Server server, String email, Date now) {
        return exists(server, Query.query(
            Criteria.where(InvitationFields.EMAIL).is(email)
                .and(InvitationFields.EXPIRES_AT).gt(now)
        ));
    }

    public Optional<Invitation> findByToken(Server server, String token) {
        return findOne(server, Query.query(Criteria.where(InvitationFields.TOKEN).is(token)));
    }

    public boolean deleteById(Server server, String id) {
        return remove(server, Query.query(Criteria.where(InvitationFields.ID).is(id))).getDeletedCount() > 0;
    }

    public void refreshToken(Server server, String invitationId, String newToken, Date newExpiresAt, Date updatedAt) {
        Query query = Query.query(Criteria.where(InvitationFields.ID).is(invitationId));
        Update update = new Update();
        update.set(InvitationFields.TOKEN, newToken);
        update.set(InvitationFields.EXPIRES_AT, newExpiresAt);
        update.set(InvitationFields.UPDATED_AT, updatedAt);
        updateFirst(server, query, update);
    }

    public void updateRoleName(Server server, String oldRoleName, String newRoleName) {
        Query query = Query.query(Criteria.where(InvitationFields.ROLE).is(oldRoleName));
        Update update = new Update().set(InvitationFields.ROLE, newRoleName);
        updateMulti(server, query, update);
    }
}
