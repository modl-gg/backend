package gg.modl.backend.auth.session;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.database.mongo.repository.AuthSessionMongoRepository;
import gg.modl.backend.server.data.Server;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final AuthSessionMongoRepository sessionRepository;
    private final AuthConfiguration authConfiguration;

    public AuthSessionData createSession(Server server, String email, String ipAddress, String userAgent) {
        sessionRepository.deleteByEmail(server, email);
        return createSessionInternal(email, ipAddress, userAgent, session -> sessionRepository.saveForServer(server, session));
    }

    public AuthSessionData createAdminSession(String email) {
        sessionRepository.deleteByEmailGlobal(email);
        return createSessionInternal(email, null, null, sessionRepository::saveForGlobal);
    }

    public List<AuthSessionData> findAllSessionsForEmail(Server server, String email) {
        return sessionRepository.findActiveByEmail(server, email, new Date());
    }

    public Optional<AuthSessionData> findValidSession(Server server, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findActiveById(server, sessionId, new Date());
    }

    public Optional<AuthSessionData> findValidAdminSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findActiveByIdGlobal(sessionId, new Date());
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
        sessionRepository.refreshExpiresAt(server, sessionId, nextExpiryDate());
    }

    public void refreshAdminSession(String sessionId) {
        sessionRepository.refreshExpiresAtGlobal(sessionId, nextExpiryDate());
    }

    public void invalidateSession(Server server, String sessionId) {
        sessionRepository.deleteById(server, sessionId);
    }

    public void invalidateAdminSession(String sessionId) {
        sessionRepository.deleteByIdGlobal(sessionId);
    }

    public void invalidateAllSessionsForEmail(Server server, String email) {
        sessionRepository.deleteByEmail(server, email);
    }

    public void invalidateAllAdminSessionsForEmail(String email) {
        sessionRepository.deleteByEmailGlobal(email);
    }

    private AuthSessionData createSessionInternal(
            String email,
            String ipAddress,
            String userAgent,
            Function<AuthSessionData, AuthSessionData> saver
    ) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Session email cannot be empty");
        }

        AuthSessionData session = new AuthSessionData();
        session.setId(generateSecureToken());
        session.setEmail(email.trim().toLowerCase(Locale.ROOT));
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);

        Date now = new Date();
        session.setCreatedAt(now);
        session.setExpiresAt(nextExpiryDate(now));
        return saver.apply(session);
    }

    private String generateSecureToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private Date nextExpiryDate() {
        return nextExpiryDate(new Date());
    }

    private Date nextExpiryDate(Date now) {
        return new Date(now.getTime() + (authConfiguration.getSessionDurationSeconds() * 1000));
    }
}
