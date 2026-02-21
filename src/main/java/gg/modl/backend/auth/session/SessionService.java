package gg.modl.backend.auth.session;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final DynamicMongoTemplateProvider mongoProvider;
    private final AuthConfiguration authConfiguration;

    public AuthSessionData createSession(Server server, String email) {
        MongoTemplate mongo = getMongoTemplateForServer(server);
        invalidateAllSessionsForEmailInternal(mongo, email);
        return createSessionInternal(mongo, email);
    }

    public AuthSessionData createAdminSession(String email) {
        MongoTemplate mongo = mongoProvider.getGlobalDatabase();
        invalidateAllSessionsForEmailInternal(mongo, email);
        return createSessionInternal(mongo, email);
    }

    private AuthSessionData createSessionInternal(MongoTemplate mongo, String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Session email cannot be empty");
        }

        AuthSessionData session = new AuthSessionData();
        session.setId(generateSecureToken());
        session.setEmail(email.trim().toLowerCase(Locale.ROOT));

        Date now = new Date();
        session.setCreatedAt(now);
        session.setExpiresAt(new Date(now.getTime() + (authConfiguration.getSessionDurationSeconds() * 1000)));

        mongo.save(session);
        return session;
    }

    public Optional<AuthSessionData> findValidSession(Server server, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        MongoTemplate mongo = getMongoTemplateForServer(server);
        return findValidSessionInternal(mongo, sessionId);
    }

    public Optional<AuthSessionData> findValidAdminSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        MongoTemplate mongo = mongoProvider.getGlobalDatabase();
        return findValidSessionInternal(mongo, sessionId);
    }

    private Optional<AuthSessionData> findValidSessionInternal(MongoTemplate mongo, String sessionId) {
        Query query = new Query(Criteria.where("_id").is(sessionId)
                .and("expiresAt").gt(new Date()));

        AuthSessionData session = mongo.findOne(query, AuthSessionData.class);
        return Optional.ofNullable(session);
    }

    public Optional<AuthSessionData> findAndRefreshSession(Server server, String sessionId) {
        Optional<AuthSessionData> sessionOpt = findValidSession(server, sessionId);
        sessionOpt.ifPresent(session -> refreshSession(server, sessionId));
        return sessionOpt;
    }

    public Optional<AuthSessionData> findAndRefreshAdminSession(String sessionId) {
        Optional<AuthSessionData> sessionOpt = findValidAdminSession(sessionId);
        sessionOpt.ifPresent(session -> refreshAdminSession(sessionId));
        return sessionOpt;
    }

    public void refreshSession(Server server, String sessionId) {
        MongoTemplate mongo = getMongoTemplateForServer(server);
        refreshSessionInternal(mongo, sessionId);
    }

    public void refreshAdminSession(String sessionId) {
        MongoTemplate mongo = mongoProvider.getGlobalDatabase();
        refreshSessionInternal(mongo, sessionId);
    }

    private void refreshSessionInternal(MongoTemplate mongo, String sessionId) {
        Date now = new Date();
        Query query = new Query(Criteria.where("_id").is(sessionId));
        Update update = new Update()
                .set("expiresAt", new Date(now.getTime() + (authConfiguration.getSessionDurationSeconds() * 1000)));

        mongo.updateFirst(query, update, AuthSessionData.class);
    }

    public void invalidateSession(Server server, String sessionId) {
        MongoTemplate mongo = getMongoTemplateForServer(server);
        invalidateSessionInternal(mongo, sessionId);
    }

    public void invalidateAdminSession(String sessionId) {
        MongoTemplate mongo = mongoProvider.getGlobalDatabase();
        invalidateSessionInternal(mongo, sessionId);
    }

    private void invalidateSessionInternal(MongoTemplate mongo, String sessionId) {
        Query query = new Query(Criteria.where("_id").is(sessionId));
        mongo.remove(query, AuthSessionData.class);
    }

    public void invalidateAllSessionsForEmail(Server server, String email) {
        MongoTemplate mongo = getMongoTemplateForServer(server);
        invalidateAllSessionsForEmailInternal(mongo, email);
    }

    public void invalidateAllAdminSessionsForEmail(String email) {
        MongoTemplate mongo = mongoProvider.getGlobalDatabase();
        invalidateAllSessionsForEmailInternal(mongo, email);
    }

    private void invalidateAllSessionsForEmailInternal(MongoTemplate mongo, String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        String rawEmail = email.trim();
        String normalizedEmail = rawEmail.toLowerCase(Locale.ROOT);

        Query query;
        if (rawEmail.equals(normalizedEmail)) {
            query = new Query(Criteria.where("email").is(normalizedEmail));
        } else {
            query = new Query(Criteria.where("email").in(rawEmail, normalizedEmail));
        }

        mongo.remove(query, AuthSessionData.class);
    }

    private MongoTemplate getMongoTemplateForServer(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }

    private String generateSecureToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
