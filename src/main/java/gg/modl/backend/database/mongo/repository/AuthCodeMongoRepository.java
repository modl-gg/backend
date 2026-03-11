package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.auth.data.AuthCode;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.MongoQueries;
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
    private final TenantMongoAccess tenantMongoAccess;

    public void replaceForServer(Server server, String normalizedEmail, String codeHash, Date expiresAt) {
        replace(tenantMongoAccess.forServer(server), normalizedEmail, codeHash, expiresAt);
    }

    private void replace(MongoTemplate template, String normalizedEmail, String codeHash, Date expiresAt) {
        template.remove(emailQuery(normalizedEmail), AuthCode.class, CollectionName.AUTH_CODES);

        AuthCode authCode = new AuthCode();
        authCode.setEmail(normalizedEmail);
        authCode.setCodeHash(codeHash);
        authCode.setExpiresAt(expiresAt);
        template.save(authCode, CollectionName.AUTH_CODES);
    }

    private Query emailQuery(String normalizedEmail) {
        return Query.query(MongoQueries.where(AuthCodeFields.EMAIL).is(normalizedEmail));
    }

    public void replaceForGlobal(String normalizedEmail, String codeHash, Date expiresAt) {
        replace(tenantMongoAccess.global(), normalizedEmail, codeHash, expiresAt);
    }

    public Optional<AuthCode> findActiveForServer(Server server, String normalizedEmail, Date now) {
        return findActive(tenantMongoAccess.forServer(server), normalizedEmail, now);
    }

    private Optional<AuthCode> findActive(MongoTemplate template, String normalizedEmail, Date now) {
        Query query = Query.query(new Criteria().andOperator(
            MongoQueries.where(AuthCodeFields.EMAIL).is(normalizedEmail),
            MongoQueries.where(AuthCodeFields.EXPIRES_AT).gt(now)
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
            MongoQueries.where(AuthCodeFields.EMAIL).is(normalizedEmail),
            MongoQueries.where(AuthCodeFields.EXPIRES_AT).gt(now)
        ));
        Update update = new Update().inc(AuthCodeFields.FAILED_ATTEMPTS, 1);
        return template.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), AuthCode.class, CollectionName.AUTH_CODES) != null;
    }

    public boolean incrementFailedAttemptsForGlobal(String normalizedEmail, Date now) {
        return incrementFailedAttempts(tenantMongoAccess.global(), normalizedEmail, now);
    }
}
