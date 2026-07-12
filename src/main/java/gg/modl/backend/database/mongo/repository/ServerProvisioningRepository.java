package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ServerProvisioningRepository extends AbstractGlobalMongoRepository<Server> {
    private static final String RESET_MESSAGE = "Database reset - awaiting reprovisioning";

    public ServerProvisioningRepository(TenantMongoAccess tenantMongoAccess) {
        super(Server.class, CollectionName.MODL_SERVERS, tenantMongoAccess);
    }

    public Optional<Server> verifyEmailTokenAtomically(String token) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ServerFields.EMAIL_VERIFICATION_TOKEN).is(token),
            Criteria.where(ServerFields.EMAIL_VERIFIED).is(false),
            Criteria.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.PENDING),
            noCleanupClaimCriteria()
        ));

        Update update = new Update()
            .set(ServerFields.EMAIL_VERIFIED, true)
            .unset(ServerFields.EMAIL_VERIFICATION_TOKEN)
            .set(ServerFields.PROVISIONING_STATUS, ProvisioningStatus.IN_PROGRESS)
            .set(ServerFields.UPDATED_AT, new Date());

        return Optional.ofNullable(findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true)
        ));
    }

    public Optional<Server> consumeProvisioningSignInToken(String token, Date now) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ServerFields.PROVISIONING_SIGN_IN_TOKEN).is(token),
            Criteria.where(ServerFields.PROVISIONING_SIGN_IN_TOKEN_EXPIRES_AT).gt(now),
            Criteria.where(ServerFields.EMAIL_VERIFIED).is(true),
            Criteria.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.COMPLETED)
        ));
        Update update = new Update()
            .unset(ServerFields.PROVISIONING_SIGN_IN_TOKEN)
            .unset(ServerFields.PROVISIONING_SIGN_IN_TOKEN_EXPIRES_AT)
            .set(ServerFields.UPDATED_AT, now);
        return Optional.ofNullable(findAndModify(query, update, FindAndModifyOptions.options().returnNew(true)));
    }

    public List<Server> findProvisioningCandidatesByIds(List<String> serverIds) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ServerFields.ID).in(serverIds),
            Criteria.where(ServerFields.DATABASE_NAME).exists(true).ne(null)
        ));
        return find(query);
    }

    public boolean markProvisioningCompleted(String serverId) {
        Query query = Query.query(Criteria.where(ServerFields.ID).is(serverId)
            .and(ServerFields.PROVISIONING_STATUS).in(
                ProvisioningStatus.IN_PROGRESS, ProvisioningStatus.PENDING, ProvisioningStatus.FAILED));
        Update update = new Update()
            .set(ServerFields.PROVISIONING_STATUS, ProvisioningStatus.COMPLETED)
            .unset(ServerFields.PROVISIONING_NOTES)
            .set(ServerFields.UPDATED_AT, new Date());
        return updateFirst(query, update).getModifiedCount() > 0;
    }

    public boolean markProvisioningFailed(String serverId, String notes) {
        Update update = new Update()
            .set(ServerFields.PROVISIONING_STATUS, ProvisioningStatus.FAILED)
            .set(ServerFields.PROVISIONING_NOTES, notes)
            .set(ServerFields.UPDATED_AT, new Date());
        return updateFirst(Query.query(Criteria.where(ServerFields.ID).is(serverId)), update)
            .getModifiedCount() > 0;
    }

    public void resetAfterDatabaseDrop(String serverId, Date updatedAt) {
        Update update = new Update()
            .set(ServerFields.PROVISIONING_STATUS, ProvisioningStatus.PENDING)
            .set(ServerFields.PROVISIONING_NOTES, RESET_MESSAGE)
            .unset(ServerFields.LAST_ACTIVITY_AT)
            .unset(ServerFields.CUSTOM_DOMAIN_STATUS)
            .unset(ServerFields.CUSTOM_DOMAIN_LAST_CHECKED)
            .unset(ServerFields.CUSTOM_DOMAIN_ERROR)
            .set(ServerFields.UPDATED_AT, updatedAt);
        updateFirst(Query.query(Criteria.where(ServerFields.ID).is(serverId)), update);
    }

    private Criteria noCleanupClaimCriteria() {
        return new Criteria().orOperator(
            Criteria.where(ServerFields.CLEANUP_CLAIM_ID).exists(false),
            Criteria.where(ServerFields.CLEANUP_CLAIM_ID).is(null)
        );
    }
}
