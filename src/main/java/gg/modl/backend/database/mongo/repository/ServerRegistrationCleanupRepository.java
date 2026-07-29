package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ServerRegistrationCleanupRepository extends AbstractGlobalMongoRepository<Server> {

    public ServerRegistrationCleanupRepository(TenantMongoAccess tenantMongoAccess) {
        super(Server.class, CollectionName.MODL_SERVERS, tenantMongoAccess);
    }

    public List<Server> findExpiredRegistrationCleanupCandidates(Date cutoff, int limit) {
        return findExpiredRegistrationCleanupCandidates(cutoff, new Date(0), limit);
    }

    public List<Server> findExpiredRegistrationCleanupCandidates(Date cutoff, Date claimCutoff, int limit) {
        Query query = Query.query(expiredRegistrationCriteria(cutoff, claimCutoff));
        query.with(Sort.by(Sort.Direction.ASC, ServerFields.CREATED_AT));
        query.limit(limit);
        query.fields()
            .include(ServerFields.SERVER_NAME)
            .include(ServerFields.CUSTOM_DOMAIN)
            .include(ServerFields.DATABASE_NAME)
            .include(ServerFields.ADMIN_EMAIL)
            .include(ServerFields.EMAIL_VERIFIED)
            .include(ServerFields.EMAIL_VERIFICATION_TOKEN)
            .include(ServerFields.PROVISIONING_STATUS)
            .include(ServerFields.API_KEY)
            .include(ServerFields.ONLINE_PLAYER_COUNT)
            .include(ServerFields.USER_COUNT)
            .include(ServerFields.TICKET_COUNT)
            .include(ServerFields.LAST_ACTIVITY_AT)
            .include(ServerFields.CREATED_AT)
            .include(ServerFields.UPDATED_AT)
            .include(ServerFields.CLEANUP_CLAIM_ID)
            .include(ServerFields.CLEANUP_CLAIMED_AT);
        return find(query);
    }

    public Optional<Server> claimExpiredRegistrationForCleanup(String serverId, Date cutoff, Instant claimedAt) {
        return claimExpiredRegistrationForCleanup(serverId, cutoff, new Date(0), claimedAt);
    }

    public Optional<Server> claimExpiredRegistrationForCleanup(String serverId, Date cutoff, Date claimCutoff, Instant claimedAt) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ServerFields.ID).is(serverId),
            expiredRegistrationCriteria(cutoff, claimCutoff)
        ));
        Update update = new Update()
            .set(ServerFields.CLEANUP_CLAIM_ID, UUID.randomUUID().toString())
            .set(ServerFields.CLEANUP_CLAIMED_AT, Date.from(claimedAt))
            .set(ServerFields.UPDATED_AT, Date.from(claimedAt));

        return Optional.ofNullable(findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true)
        ));
    }

    public boolean deleteClaimedExpiredRegistration(String serverId, String cleanupClaimId, Date cutoff) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ServerFields.ID).is(serverId),
            Criteria.where(ServerFields.CLEANUP_CLAIM_ID).is(cleanupClaimId),
            explicitExpiredRegistrationCriteria(cutoff)
        ));
        return remove(query).getDeletedCount() > 0;
    }

    public Optional<Server> confirmRegistrationCleanupClaim(String serverId, String cleanupClaimId, Date cutoff, Instant confirmedAt) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ServerFields.ID).is(serverId),
            Criteria.where(ServerFields.CLEANUP_CLAIM_ID).is(cleanupClaimId),
            explicitExpiredRegistrationCriteria(cutoff)
        ));
        Update update = new Update()
            .set(ServerFields.CLEANUP_CLAIMED_AT, Date.from(confirmedAt))
            .set(ServerFields.UPDATED_AT, Date.from(confirmedAt));

        return Optional.ofNullable(findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true)
        ));
    }

    public boolean releaseRegistrationCleanupClaim(String serverId, String cleanupClaimId) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ServerFields.ID).is(serverId),
            Criteria.where(ServerFields.CLEANUP_CLAIM_ID).is(cleanupClaimId)
        ));
        Update update = new Update()
            .unset(ServerFields.CLEANUP_CLAIM_ID)
            .unset(ServerFields.CLEANUP_CLAIMED_AT)
            .set(ServerFields.UPDATED_AT, new Date());
        return updateFirst(query, update).getModifiedCount() > 0;
    }

    private Criteria expiredRegistrationCriteria(Date cutoff, Date claimCutoff) {
        return new Criteria().andOperator(
            explicitExpiredRegistrationCriteria(cutoff),
            cleanupClaimEligibleCriteria(claimCutoff)
        );
    }

    private Criteria explicitExpiredRegistrationCriteria(Date cutoff) {
        return new Criteria().andOperator(
            Criteria.where(ServerFields.EMAIL_VERIFIED).is(false),
            Criteria.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.PENDING),
            Criteria.where(ServerFields.EMAIL_VERIFICATION_TOKEN).exists(true).nin(null, ""),
            Criteria.where(ServerFields.CREATED_AT).exists(true).lt(cutoff),
            Criteria.where(ServerFields.DATABASE_NAME).regex("^server_.+"),
            notPresentOrBlank(ServerFields.API_KEY),
            notPresent(ServerFields.LAST_ACTIVITY_AT),
            notPositive(ServerFields.USER_COUNT),
            notPositive(ServerFields.TICKET_COUNT),
            notPositive(ServerFields.ONLINE_PLAYER_COUNT)
        );
    }

    private Criteria cleanupClaimEligibleCriteria(Date claimCutoff) {
        return new Criteria().orOperator(
            Criteria.where(ServerFields.CLEANUP_CLAIM_ID).exists(false),
            Criteria.where(ServerFields.CLEANUP_CLAIM_ID).is(null),
            Criteria.where(ServerFields.CLEANUP_CLAIMED_AT).lt(claimCutoff)
        );
    }

    private Criteria notPresent(String field) {
        return new Criteria().orOperator(
            Criteria.where(field).exists(false),
            Criteria.where(field).is(null)
        );
    }

    private Criteria notPresentOrBlank(String field) {
        return new Criteria().orOperator(
            Criteria.where(field).exists(false),
            Criteria.where(field).is(null),
            Criteria.where(field).is("")
        );
    }

    private Criteria notPositive(String field) {
        return new Criteria().orOperator(
            Criteria.where(field).exists(false),
            Criteria.where(field).is(null),
            Criteria.where(field).lte(0)
        );
    }
}
