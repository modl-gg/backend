package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.auth.session.AuthSessionData;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.AuthSessionDataFields;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuthSessionMongoRepository {
    private final TenantMongoAccess tenantMongoAccess;

    public AuthSessionData saveForServer(Server server, AuthSessionData session) {
        return tenantMongoAccess.forServer(server).save(session, CollectionName.SESSIONS);
    }

    public AuthSessionData saveForGlobal(AuthSessionData session) {
        return tenantMongoAccess.global().save(session, CollectionName.SESSIONS);
    }

    public List<AuthSessionData> findActiveByEmail(Server server, String email, Date now) {
        return findActiveByEmail(tenantMongoAccess.forServer(server), normalize(email), now);
    }

    public void deleteByEmail(Server server, String email) {
        deleteByEmailInternal(tenantMongoAccess.forServer(server), email);
    }

    public void deleteByEmailGlobal(String email) {
        deleteByEmailInternal(tenantMongoAccess.global(), email);
    }

    public Optional<AuthSessionData> findActiveById(Server server, String sessionId, Date now) {
        return findActiveById(tenantMongoAccess.forServer(server), sessionId, now);
    }

    public Optional<AuthSessionData> findActiveByIdGlobal(String sessionId, Date now) {
        return findActiveById(tenantMongoAccess.global(), sessionId, now);
    }

    public boolean refreshExpiresAt(Server server, String sessionId, Date expiresAt) {
        return refreshExpiresAt(tenantMongoAccess.forServer(server), sessionId, expiresAt);
    }

    public boolean refreshExpiresAtGlobal(String sessionId, Date expiresAt) {
        return refreshExpiresAt(tenantMongoAccess.global(), sessionId, expiresAt);
    }

    public void deleteById(Server server, String sessionId) {
        tenantMongoAccess.forServer(server).remove(idQuery(sessionId), AuthSessionData.class, CollectionName.SESSIONS);
    }

    public void deleteByIdGlobal(String sessionId) {
        tenantMongoAccess.global().remove(idQuery(sessionId), AuthSessionData.class, CollectionName.SESSIONS);
    }

    private List<AuthSessionData> findActiveByEmail(MongoTemplate template, String normalizedEmail, Date now) {
        Query query = Query.query(new Criteria().andOperator(
                MongoQueries.where(AuthSessionDataFields.EMAIL).is(normalizedEmail),
                MongoQueries.where(AuthSessionDataFields.EXPIRES_AT).gt(now)
        ));
        return template.find(query, AuthSessionData.class, CollectionName.SESSIONS);
    }

    private Optional<AuthSessionData> findActiveById(MongoTemplate template, String sessionId, Date now) {
        Query query = Query.query(new Criteria().andOperator(
                MongoQueries.where(AuthSessionDataFields.ID).is(sessionId),
                MongoQueries.where(AuthSessionDataFields.EXPIRES_AT).gt(now)
        ));
        return Optional.ofNullable(template.findOne(query, AuthSessionData.class, CollectionName.SESSIONS));
    }

    private boolean refreshExpiresAt(MongoTemplate template, String sessionId, Date expiresAt) {
        Update update = new Update().set(AuthSessionDataFields.EXPIRES_AT, expiresAt);
        return template.updateFirst(idQuery(sessionId), update, AuthSessionData.class, CollectionName.SESSIONS).getMatchedCount() > 0;
    }

    private void deleteByEmailInternal(MongoTemplate template, String email) {
        String rawEmail = email == null ? null : email.trim();
        if (rawEmail == null || rawEmail.isBlank()) {
            return;
        }

        String normalizedEmail = normalize(rawEmail);
        Query query = rawEmail.equals(normalizedEmail)
                ? Query.query(MongoQueries.where(AuthSessionDataFields.EMAIL).is(normalizedEmail))
                : Query.query(MongoQueries.where(AuthSessionDataFields.EMAIL).in(rawEmail, normalizedEmail));
        template.remove(query, AuthSessionData.class, CollectionName.SESSIONS);
    }

    private Query idQuery(String sessionId) {
        return Query.query(MongoQueries.where(AuthSessionDataFields.ID).is(sessionId));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
