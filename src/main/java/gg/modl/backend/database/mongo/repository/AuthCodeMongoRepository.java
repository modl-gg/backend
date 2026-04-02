package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.auth.data.AuthCode;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.AuthCodeFields;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuthCodeMongoRepository {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private final TenantMongoAccess tenantMongoAccess;

    public void replaceForServer(Server server, String normalizedEmail, String codeHash, Date expiresAt) {
        replace(tenantMongoAccess.forServer(server), normalizedEmail, codeHash, expiresAt);
    }

    private void replace(MongoTemplate template, String normalizedEmail, String codeHash, Date expiresAt) {
        Query query = Query.query(Criteria.where(AuthCodeFields.EMAIL).is(normalizedEmail));
        Update update = new Update()
            .set(AuthCodeFields.CODE_HASH, codeHash)
            .set(AuthCodeFields.EXPIRES_AT, expiresAt)
            .set(AuthCodeFields.FAILED_ATTEMPTS, 0)
            .setOnInsert(AuthCodeFields.EMAIL, normalizedEmail);
        template.upsert(query, update, AuthCode.class, CollectionName.AUTH_CODES);
    }

    private Query emailQuery(String normalizedEmail) {
        return Query.query(Criteria.where(AuthCodeFields.EMAIL).is(normalizedEmail));
    }

    public void replaceForGlobal(String normalizedEmail, String codeHash, Date expiresAt) {
        replace(tenantMongoAccess.global(), normalizedEmail, codeHash, expiresAt);
    }

    public Optional<AuthCode> findActiveForServer(Server server, String normalizedEmail, Date now) {
        return findActive(tenantMongoAccess.forServer(server), normalizedEmail, now);
    }

    private Optional<AuthCode> findActive(MongoTemplate template, String normalizedEmail, Date now) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(AuthCodeFields.EMAIL).is(normalizedEmail),
            Criteria.where(AuthCodeFields.EXPIRES_AT).gt(now)
        ));
        return Optional.ofNullable(template.findOne(query, AuthCode.class, CollectionName.AUTH_CODES));
    }

    public Optional<AuthCode> findActiveForGlobal(String normalizedEmail, Date now) {
        return findActive(tenantMongoAccess.global(), normalizedEmail, now);
    }

    public void deleteForServer(Server server, String normalizedEmail) {
        tenantMongoAccess.forServer(server).remove(emailQuery(normalizedEmail), AuthCode.class, CollectionName.AUTH_CODES);
    }

    public void deleteForGlobal(String normalizedEmail) {
        tenantMongoAccess.global().remove(emailQuery(normalizedEmail), AuthCode.class, CollectionName.AUTH_CODES);
    }

    public boolean incrementFailedAttemptsForServer(Server server, String normalizedEmail, Date now) {
        return incrementFailedAttempts(tenantMongoAccess.forServer(server), normalizedEmail, now);
    }

    private boolean incrementFailedAttempts(MongoTemplate template, String normalizedEmail, Date now) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(AuthCodeFields.EMAIL).is(normalizedEmail),
            Criteria.where(AuthCodeFields.EXPIRES_AT).gt(now)
        ));
        Update update = new Update().inc(AuthCodeFields.FAILED_ATTEMPTS, 1);
        return template.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), AuthCode.class, CollectionName.AUTH_CODES) != null;
    }

    public boolean incrementFailedAttemptsForGlobal(String normalizedEmail, Date now) {
        return incrementFailedAttempts(tenantMongoAccess.global(), normalizedEmail, now);
    }

    public Optional<AuthCode> consumeIfHashMatchesForServer(Server server, String normalizedEmail, String codeHash, Date now) {
        return consumeIfHashMatches(tenantMongoAccess.forServer(server), normalizedEmail, codeHash, now);
    }

    public Optional<AuthCode> consumeIfHashMatchesForGlobal(String normalizedEmail, String codeHash, Date now) {
        return consumeIfHashMatches(tenantMongoAccess.global(), normalizedEmail, codeHash, now);
    }

    private Optional<AuthCode> consumeIfHashMatches(MongoTemplate template, String normalizedEmail, String codeHash, Date now) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(AuthCodeFields.EMAIL).is(normalizedEmail),
            Criteria.where(AuthCodeFields.EXPIRES_AT).gt(now),
            Criteria.where(AuthCodeFields.CODE_HASH).is(codeHash),
            Criteria.where(AuthCodeFields.FAILED_ATTEMPTS).lt(MAX_FAILED_ATTEMPTS)
        ));
        return Optional.ofNullable(template.findAndRemove(query, AuthCode.class, CollectionName.AUTH_CODES));
    }
}
